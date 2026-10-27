import copy
import unittest
from inference_parity import equivalent

class ParityTests(unittest.TestCase):
    def setUp(self):
        self.rows=[dict(action=a, parameters={'enabled':True}, score=s,
                        slot_scores={'enabled':1.0}) for a,s in [('A',1.0),('B',0.0),('C',0.0)]]

    def test_zero_tail_order_is_irrelevant(self):
        self.assertTrue(equivalent(self.rows,[self.rows[0],self.rows[2],self.rows[1]]))

    def test_winner_and_parameters_are_strict(self):
        for key,value in [('action','B'),('parameters',{'enabled':False}),('score',.99)]:
            other=copy.deepcopy(self.rows);other[0][key]=value
            self.assertFalse(equivalent(self.rows,other))

    def test_execution_and_slot_thresholds_are_strict(self):
        for field,low,high in [('score',.9949,.995),('slot',.6499,.65)]:
            a=copy.deepcopy(self.rows);b=copy.deepcopy(self.rows)
            if field=='score':a[0]['score']=low;b[0]['score']=high
            else:a[0]['slot_scores']['enabled']=low;b[0]['slot_scores']['enabled']=high
            self.assertFalse(equivalent(a,b))

    def test_nonzero_alternatives_are_strict(self):
        self.rows[1]['score']=.01
        self.assertFalse(equivalent(self.rows,[self.rows[0],self.rows[2],self.rows[1]]))

if __name__=='__main__':unittest.main()
