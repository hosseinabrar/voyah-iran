package android.graphics; public class Rect {
 public int left,top,right,bottom; public Rect(){} public Rect(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}
 public Rect(Rect r){set(r);} public void set(Rect r){left=r.left;top=r.top;right=r.right;bottom=r.bottom;}
 public int width(){return right-left;} public int height(){return bottom-top;} public boolean isEmpty(){return left>=right||top>=bottom;}
 public boolean intersect(Rect r){int l=Math.max(left,r.left),t=Math.max(top,r.top),rr=Math.min(right,r.right),b=Math.min(bottom,r.bottom);
 if(l>=rr||t>=b)return false;left=l;top=t;right=rr;bottom=b;return true;}
 public String toString(){return left+","+top+","+right+","+bottom;}
}