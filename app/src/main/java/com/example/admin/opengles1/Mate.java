package com.example.admin.opengles1;

import static java.lang.Float.NaN;

public class Mate {
    public static float epsilon=0.0000000001f;

    public static boolean nearZero(float a)
    {
        return Math.abs(a)<epsilon;
    }

    public static float dot(float[] v1, float[] v2) {
        return v1[0] * v2[0] + v1[1] * v2[1] + v1[2] * v2[2];
    }

    public static float[] multiplyVectorScalar(float[] v, float s) {
        float[] rv = new float[4];
        rv[0] = v[0] * s;
        rv[1] = v[1] * s;
        rv[2] = v[2] * s;
        rv[3] = 1;

        return rv;
    }

    public static float[] multiplyVectorScalarApplied(float[] v, float s) {
        v[0]*=s;
        v[1]*=s;
        v[2]*=s;
        return v;
    }

    public static float[] addVectors(float[] v1, float[] v2) {
        float[] rv = new float[4];
        rv[0] = v1[0] + v2[0];
        rv[1] = v1[1] + v2[1];
        rv[2] = v1[2] + v2[2];
        rv[3] = 1;
        return rv;
    }

    public static float[] addVectorsApplied(float[] v1, float[] v2) {
        v1[0]+=v2[0];
        v1[1]+=v2[1];
        v1[2]+=v2[2];
        return v1;
    }

    public static float[] subsVectors(float[] v1, float[] v2) {
        float[] rv = new float[4];
        rv[0] = v1[0] - v2[0];
        rv[1] = v1[1] - v2[1];
        rv[2] = v1[2] - v2[2];
        rv[3] = 1;
        return rv;
    }

    public static float[] subsVectorsApplied(float[] v1, float[] v2) {
        v1[0]-=v2[0];
        v1[1]-=v2[1];
        v1[2]-=v2[2];
        return v1;
    }

    public static float[] crossProduct(float[] v1, float[] v2) {
        float[] rv = new float[4];
        rv[0] = v1[1] * v2[2] - v1[2] * v2[1];
        rv[1] = v1[2] * v2[0] - v1[0] * v2[2];
        rv[2] = v1[0] * v2[1] - v1[1] * v2[0];
        rv[3] = 1;
        return rv;
    }

    // -b +- sqrt(b*b-4ac) / 2a
    public static float minCuadraticRoot(float A, float B, float C) {
        float D = B * B - 4 * A * C;
        if (nearZero(A) || D < 0)
            return NaN;

        float sqrtD = (float) Math.sqrt(D);
        return Math.min((-B + sqrtD) / (2 * A), (-B - sqrtD) / (2 * A));
    }

    public static float[] normalize(float[] v) {
        float[] rv;
        float len=(float)Math.sqrt(Mate.dot(v,v));
        if(len>epsilon)
            rv=multiplyVectorScalar(v,1/len);
        else
            rv=multiplyVectorScalar(v,1);
        return rv;
    }

    public static float[] normalizeApplied(float[] v) {
        float len=(float)Math.sqrt(Mate.dot(v,v));
        if(len>epsilon)
            multiplyVectorScalarApplied(v,1/len);
        return v;
    }
}