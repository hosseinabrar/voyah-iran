"""Compare execution-relevant predictions, ignoring only zero-score tail ordering."""
import math

def gate(candidates):
    return (candidates[0]['action'] != 'UNKNOWN'
            and math.floor(candidates[0]['score'] * 100 + .5) == 100
            and candidates[0]['score'] - candidates[1]['score'] >= .15)

def equivalent(expected, actual):
    if len(expected) != len(actual) or gate(expected) != gate(actual):
        return False
    for index, (a, b) in enumerate(zip(expected, actual)):
        # Numerically negligible alternatives can exchange rank after export.
        # Never waive comparison of the winning prediction, even at zero.
        if index and a['score'] == b['score'] == 0:
            continue
        if (a['action'] != b['action'] or a['parameters'] != b['parameters']
                or abs(a['score'] - b['score']) > .0002):
            return False
        left, right = a['slot_scores'], b['slot_scores']
        if left.keys() != right.keys():
            return False
        if any(abs(left[k] - right[k]) > .0002 or (left[k] < .65) != (right[k] < .65) for k in left):
            return False
    return True
