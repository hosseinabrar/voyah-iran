"""Frozen INT8 encoder with trainable FP32 heads; no FP32 encoder fallback."""
from pathlib import Path
from types import SimpleNamespace
import shutil
import torch
import onnx
import onnxruntime as ort


class CompactEncoder(torch.nn.Module):
    def __init__(self, path):
        super().__init__()
        options = ort.SessionOptions()
        options.intra_op_num_threads = 4
        self.session = ort.InferenceSession(str(path), options, providers=['CPUExecutionProvider'])

    def forward(self, **inputs):
        # The deployed export has fixed batch=1; preserve padded sequence lengths
        # for replay training while running each row through that exact graph.
        rows = []
        for i in range(inputs['input_ids'].shape[0]):
            feed = {key: inputs[key][i:i+1].cpu().numpy() for key in
                    ['input_ids', 'attention_mask', 'token_type_ids']}
            rows.append(torch.from_numpy(self.session.run(None, feed)[0]))
        return SimpleNamespace(last_hidden_state=torch.cat(rows))


def activate(model, root, original_checkpoint):
    directory = root/'artifacts/compact-web'
    directory.mkdir(exist_ok=True)
    target = directory/'encoder.int8.onnx'
    if not target.exists():
        source = root/'artifacts/android/model.int8.onnx'
        graph = onnx.load(str(source))
        # Select the final encoder representation, before pooling and heads.
        multiply = next(n for n in graph.graph.node if '/model/Mul_output_0' in n.output)
        hidden = next(v for v in multiply.input if 'encoder/layer.11/output/LayerNorm' in v)
        graph.graph.output.append(onnx.helper.make_tensor_value_info(hidden, onnx.TensorProto.FLOAT, [1, 'sequence', 768]))
        extracted = onnx.utils.Extractor(graph).extract_model(
            ['input_ids', 'attention_mask', 'token_type_ids'], [hidden])
        onnx.checker.check_model(extracted)
        onnx.save(extracted, str(target))
    checkpoint = directory/'feedback-head.safetensors'
    if not checkpoint.exists():
        shutil.copy2(original_checkpoint, checkpoint)
    model.bert = CompactEncoder(target)
    return 'compact-web/feedback-head'
