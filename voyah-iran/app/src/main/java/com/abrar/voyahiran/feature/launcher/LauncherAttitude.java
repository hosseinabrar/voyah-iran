package com.abrar.voyahiran.feature.launcher;
/** Sensor-axis convention and attitude filter used by the factory CCC compass. */
final class LauncherAttitude {
    float q0=1, q1=0, q2=0, q3=0, exInt=0, eyInt=0, ezInt=0;
    double mDipAngle=Double.NaN, mSlope=Double.NaN;
    void calculateDipAngleSlope(float f, float f2, float f3, float f4, float f5, float f6) {
        try {
            float f7 = this.q0 * this.q0;
            float f8 = this.q0 * this.q1;
            float f9 = this.q0 * this.q2;
            float f10 = this.q1 * this.q1;
            float f11 = this.q1 * this.q3;
            float f12 = this.q2 * this.q2;
            float f13 = this.q2 * this.q3;
            float f14 = this.q3 * this.q3;
            if (f4 * f5 * f6 == 0.0f) {
                return;
            }
            float fSqrt = (float) Math.sqrt((f4 * f4) + (f5 * f5) + (f6 * f6));
            float f15 = f4 / fSqrt;
            float f16 = f5 / fSqrt;
            float f17 = f6 / fSqrt;
            float f18 = (f11 - f9) * 2.0f;
            float f19 = (f8 + f13) * 2.0f;
            float f20 = ((f7 - f10) - f12) + f14;
            float f21 = (f16 * f20) - (f17 * f19);
            float f22 = (f17 * f18) - (f20 * f15);
            float f23 = (f15 * f19) - (f16 * f18);
            float f24 = this.exInt + (f21 * 0.008f);
            this.exInt = f24;
            float f25 = this.eyInt + (f22 * 0.008f);
            this.eyInt = f25;
            float f26 = this.ezInt + (0.008f * f23);
            this.ezInt = f26;
            float f27 = f + (f21 * 10.0f) + f24;
            float f28 = f2 + (f22 * 10.0f) + f25;
            float f29 = f3 + (f23 * 10.0f) + f26;
            float f30 = this.q0;
            float f31 = this.q1;
            float f32 = this.q2;
            float f33 = this.q3;
            float f34 = this.q0 + (((((-f31) * f27) - (f32 * f28)) - (f33 * f29)) * 0.001f);
            this.q0 = f34;
            float f35 = this.q1 + ((((f30 * f27) + (f32 * f29)) - (f33 * f28)) * 0.001f);
            this.q1 = f35;
            float f36 = this.q2 + ((((f30 * f28) - (f31 * f29)) + (f33 * f27)) * 0.001f);
            this.q2 = f36;
            float f37 = this.q3 + ((((f30 * f29) + (f31 * f28)) - (f32 * f27)) * 0.001f);
            this.q3 = f37;
            float fSqrt2 = (float) Math.sqrt((f34 * f34) + (f35 * f35) + (f36 * f36) + (f37 * f37));
            float f38 = this.q0 / fSqrt2;
            this.q0 = f38;
            float f39 = this.q1 / fSqrt2;
            this.q1 = f39;
            float f40 = this.q2 / fSqrt2;
            this.q2 = f40;
            float f41 = this.q3 / fSqrt2;
            this.q3 = f41;
            this.mDipAngle = Math.asin((f39 * (-2.0f) * f41) + (f38 * 2.0f * f40)) * 57.3d;
            double dAtan2 = 57.3d * Math.atan2((this.q2 * 2.0f * this.q3) + (this.q0 * 2.0f * this.q1), (((this.q1 * (-2.0f)) * this.q1) - ((this.q2 * 2.0f) * this.q2)) + 1.0f);
            this.mSlope = dAtan2;
            
        } catch (Exception unused) {
        }
    }


}
