"""Freeze the exact web INT8 encoder and latest trained FP32 heads into one graph."""
import json,shutil
import numpy as np
import onnx
from onnx import helper as h, numpy_helper as nh, TensorProto as T
from export_android import ROOT,OUT,load_active,sha

def main():
    OUT.mkdir(exist_ok=True)
    checkpoint=ROOT/'artifacts/compact-web/feedback-head.safetensors'
    shutil.copyfile(checkpoint,OUT/'feedback-snapshot.safetensors')
    model,tokenizer,trainer,source,feedback_sha=load_active()
    encoder=ROOT/'artifacts/compact-web/encoder.int8.onnx'
    graph=onnx.load(str(encoder));hidden=graph.graph.output[0].name
    del graph.graph.output[:]
    def const(name,value):graph.graph.initializer.append(nh.from_array(np.asarray(value),name))
    const('web_axes2',np.array([2],dtype=np.int64));const('web_axes1',np.array([1],dtype=np.int64))
    graph.graph.node.extend([
        h.make_node('Cast',['attention_mask'],['web_mask'],to=T.FLOAT),
        h.make_node('Unsqueeze',['web_mask','web_axes2'],['web_mask3']),
        h.make_node('Mul',[hidden,'web_mask3'],['web_masked']),
        h.make_node('ReduceSum',['web_masked','web_axes1'],['web_sum'],keepdims=0),
        h.make_node('ReduceSum',['web_mask3','web_axes1'],['web_count'],keepdims=0),
        h.make_node('Div',['web_sum','web_count'],['web_pooled']),
    ])
    heads=[('frames',model.frame_head)]+[('slot_'+k,model.slot_heads['slot_'+k]) for k in model.schema['slots']]
    for name,head in heads:
        const('web_'+name+'_w',head.weight.detach().numpy())
        const('web_'+name+'_b',head.bias.detach().numpy())
        graph.graph.node.append(h.make_node('Gemm',['web_pooled','web_'+name+'_w','web_'+name+'_b'],[name],transB=1))
        graph.graph.output.append(h.make_tensor_value_info(name,T.FLOAT,[1,head.out_features]))
    onnx.checker.check_model(graph)
    onnx.save(graph,str(OUT/'model.compact.onnx'))
    (OUT/'schema.json').write_text(json.dumps(model.schema,ensure_ascii=False))
    tokenizer.save_pretrained(OUT/'tokenizer')
    (OUT/'contact-rules.json').write_text(json.dumps(trainer.contact_rules,ensure_ascii=False))
    (OUT/'provenance.json').write_text(json.dumps(dict(source=str(source.relative_to(ROOT)),
        base_sha256=sha(source/'model.safetensors'),feedback_sha256=feedback_sha,
        feedback_count=len(trainer.rows),outputs=[n for n,_ in heads],max_tokens=48,
        precision='int8-encoder-fp32-heads',graph='model.compact.onnx',encoder_sha256=sha(encoder)),indent=2))
    print('Exported',len(trainer.rows),'feedback records;', (OUT/'model.compact.onnx').stat().st_size,'bytes',flush=True)

if __name__=='__main__':main()
