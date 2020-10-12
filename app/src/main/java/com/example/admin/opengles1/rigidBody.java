package com.example.admin.opengles1;

import android.opengl.Matrix;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

enum location {fixed, boneAtached};

class bodySpace
{
    location tipo;
    float radius;
    float yShift;
    int boneId;
    float height;
    boolean collisionTest;
    boolean rayTest;
    //float locx,locy,locz;   // posiciones calculadas

    public bodySpace(float radiusSize, float heightSize, float shiftVert, boolean collision, boolean ray)
    {
        tipo=location.fixed;
        radius=radiusSize;
        yShift=shiftVert;
        height=heightSize;
        collisionTest=collision;
        rayTest=ray;
    }

    public bodySpace(float radiusSize, float heightSize,int boneNumber, float shiftVert, boolean collision, boolean ray)
    {
        tipo=location.boneAtached;
        radius=radiusSize;
        boneId=boneNumber;
        yShift=shiftVert;
        height=heightSize;
        collisionTest=collision;
        rayTest=ray;
    }
};


class rTriangle
{
    public float[] mp=new float[3];      // main point, no se para que lo guardo
    public float[] bMat=new float[16];   // Matriz a modelo baricentrico
    public float[] nor=new float[3];     // normal a la superficie (normalizada)
    public float[] limInf=new float[3];  // limites inferior del cuerpo en el espacio
    public float[] limSup=new float[3];  // limites superior del cuerpo en el espacio
    // hitscan
    //public float[] center=new float[3];  // Centro del triangulo
    //public float radius;

    public rTriangle(float[] p1, float[] p2, float[] p3)
    {
        float[] v1=new float[3];
        float[] v2=new float[3];

        for(int i=0;i<3;i++)
        {
            mp[i]=p1[i];              // guardo el punto base
            v1[i] = p2[i] - p1[i];    // v1=p2-p1
            v2[i] = p3[i] - p1[i];    // v2=p3-p1
            limInf[i]=Math.min(Math.min(p1[i],p2[i]),p3[i]); // calculo de limite inferior
            limSup[i]=Math.max(Math.max(p1[i],p2[i]),p3[i]);  // calculo de limite superior
            //center[i]=(p1[i]+p2[i]+p3[i])/3;    // centroide
        }
        //radius=Math.max(Math.max(limSup[1]-limInf[1],limSup[2]-limInf[2]),limSup[0]-limInf[0])/2;

        nor[0]=v1[1]*v2[2]-v1[2]*v2[1];
        nor[1]=v1[2]*v2[0]-v1[0]*v2[2];
        nor[2]=v1[0]*v2[1]-v1[1]*v2[0];
        double s=Math.sqrt(nor[0]*nor[0]+nor[1]*nor[1]+nor[2]*nor[2]);
        for(int i=0;i<3;i++)
            nor[i]=(float)(nor[i]/s);

        float[] mat=new float[16]; // Matriz de modelo baricentrico a cartesiano
        mat[0]=v1[0];   mat[4]=v2[0];   mat[8]=nor[0];  mat[12]=p1[0];
        mat[1]=v1[1];   mat[5]=v2[1];   mat[9]=nor[1];  mat[13]=p1[1];
        mat[2]=v1[2];   mat[6]=v2[2];   mat[10]=nor[2]; mat[14]=p1[2];
        mat[3]=0;       mat[7]=0;       mat[11]=0;      mat[15]=1;

        // Calculo matriz de cartesiano a barycentrico
        Matrix.invertM(bMat,0,mat,0);
    }

    // calcula si un tringulo intersecta con una esfera y devuelve cuanto deberia moverse para evitarlo
    public boolean test(float[] centro, float radio, float[] reaccion)
    {
        float[] cartePos=new float[4];  // cartesiano
        float[] baryPos=new float[4];   // baricentrico?

        for(int i=0;i<3;i++)
            cartePos[i]=centro[i]+reaccion[i];
        cartePos[3]=1;

        // Fuera de zona limite de influencia, primer chequeo
        for(int i=0;i<3;i++)
            if(cartePos[i]+radio<limInf[i] || cartePos[i]-radio>limSup[i])
                return false;

        // Convierto a coordenadas barycentricas
        Matrix.multiplyMV(baryPos,0,bMat,0,cartePos,0);

        // fuera del triangulo o mas lejos que el radio
        if(baryPos[0]<0 || baryPos[1]<0 || baryPos[0]+baryPos[1]>1 || Math.abs(baryPos[2])>radio)
            return false;

        for(int i=0;i<3;i++)
            reaccion[i]+=nor[i]*(radio-baryPos[2]);

        return true;
    }
}

class rigidFloor
{
    public rTriangle[] bordes;
    public int count;
    public float[] limInf;  // limites inferior del cuerpo en el espacio
    public float[] limSup;  // limites superior del cuerpo en el espacio

    public rigidFloor(entity e)
    {
        float[] mat = e.getWorldMatrix();
        float[] p1=new float[4];
        float[] p2=new float[4];
        float[] p3=new float[4];
        limInf=new float[3];
        limSup=new float[3];

        count=e.mesh.caras.size();
        bordes=new rTriangle[count];

        for(int i=0; i<count;i++)
        {
            transform(p1, mat, e.mesh.vertices.get(e.mesh.caras.get(i).v1).co);
            transform(p2, mat, e.mesh.vertices.get(e.mesh.caras.get(i).v2).co);
            transform(p3, mat, e.mesh.vertices.get(e.mesh.caras.get(i).v3).co);
            bordes[i]=new rTriangle(p1,p2,p3);
            for(int z=0;z<3;z++) {
                limInf[z] = Math.min(limInf[z], bordes[i].limInf[z]); // calculo de limite inferior
                limSup[z] = Math.max(limSup[z], bordes[i].limSup[z]);  // calculo de limite superior
            }
        }
    }

    // calcula si un tringulo intersecta con una esfera y devuelve cuanto deberia moverse para evitarlo
    public boolean test(float[] centro, float radio, float[] reaccion)
    {
        for(int i=0;i<3;i++)
            reaccion[i]=0;

        // Fuera de zona limite de influencia general, primer chequeo
        for(int i=0;i<3;i++)
            if(centro[i]+radio<limInf[i] || centro[i]-radio>limSup[i])
                return false;

        boolean rc=false;
        for(int i=0; i<count;i++)
            rc=rc||bordes[i].test(centro, radio, reaccion);

        return rc;
    }

    void transform(float[] v4, float[] mat16, float[] vec3)
    {
        float[] vec = new float[4];
        vec[0]=vec3[0];
        vec[1]=vec3[1];
        vec[2]=vec3[2];
        vec[3]=1;
        Matrix.multiplyMV(v4, 0, mat16, 0, vec, 0);
    }
}

class rigidBody extends entity {
    public float[] vel;
    public float[] vel2apply;
    public float[] loc2apply;
    //float radius;
    //float vertShift;
    public boolean collision;
    List<bodySpace> espacios = new ArrayList<bodySpace>();

    // variables para debugear
    public int _vertexCount;
    public FloatBuffer _vertexBuffer;
    public FloatBuffer _normalBuffer;
    public FloatBuffer _boneBuffer;
    public FloatBuffer _weightBuffer;
    public boolean OGL_Calc=false;  // para calcular os datos la primera vez que los necesito

    public rigidBody(String meshName) {
        super(meshName);
        vel = new float[3];
        vel2apply = new float[3];
        loc2apply = new float[3];

        for (int i = 0; i < 3; i++) {
            vel[i] = 0;
            vel2apply[i] = 0;
            loc2apply[i] = 0;
        }
        collision = false;
    }

    // Agrega una esfera fija con un radio y elevado en SHIFT
    void addFixedSpace(float radiusSize, float heightSize, float shiftVert, boolean collision, boolean ray) {
        espacios.add(new bodySpace(radiusSize, heightSize, shiftVert, collision, ray));
    }

    // crea una esfera ligada a un hueso en particular
    void addBoneRelatedSpace(float radiusSize, float heightSize, String boneName, float shiftVert, boolean collision, boolean ray) {
        if (mesh.skeleton) {
            int b = mesh.getBoneIdByName(boneName);
            if (b >= 0)
                espacios.add(new bodySpace(radiusSize, heightSize, b, shiftVert, collision, ray));
        }
    }

    // ahora es seguro hacer la destruccion?
    public void destroy() {
        super.destroy();
    }

    public void applyDt(float dt) {
        vel[0] += vel2apply[0];
        if (vel2apply[1] > 0)
            vel[1] += vel2apply[1];
        vel[2] += vel2apply[2];
        vel2apply[0] = vel2apply[1] = vel2apply[2] = 0;

        vel[1] -= 9.8f * dt;   // aceleracion
        addLocation(vel[0] * dt, vel[1] * dt, vel[2] * dt); // velocidad
        addLocation(loc2apply[0], loc2apply[1], loc2apply[2]); // moviminto fijo
        Log.d("MyApp", "vel=" + vel[1] + " dt=" + dt);
        Log.d("MyApp", "Move:loc2apply=" + loc2apply[0] + " ; " + loc2apply[2]);
        collision = false;
    }

    public void applyReaction(float x, float y, float z, float dt) {
        Log.d("MyApp", "Reaccion=" + x + ":" + y + ":" + z);
        // Posicion
        addLocation(x, y, z);
        // Velocidad
        float s = (float) Math.sqrt(x * x + y * y + z * z);
        if (s > 0.001) {
            float d = Math.abs((x * vel[0] + y * vel[1] + z * vel[2]) / s);
            // La velocidad solo en la componente de la reaccion para no patinar
            // para patinar poner un mas como en "vel[0]+=d*x/s;"
            if (y / s > 0.7) // cos inclinacion maxima => menos angulo de inclinacion => freno todo
                vel[0] = vel[1] = vel[2] = 0; // saque vel1 para poder saltar
            else {
                vel[0] += d * x / s; //vel[0]*=(1-dt);
                vel[1] += d * y / s; //vel[1]*=(1-dt);
                vel[2] += d * z / s; //vel[2]*=(1-dt);
            }
        }
        collision = true;
        Log.d("MyApp", "Rigid:" + String.valueOf(dt) + ":" + String.valueOf(x) + "," + String.valueOf(y) + "," + String.valueOf(z));
    }

    // ademas de moverlo lo frena
    public void move(float x, float y, float z) {
        loc2apply[0] += x;
        loc2apply[1] += y;
        loc2apply[2] += z;
        //vel[0]=vel[2]=0;
        //addLocation(x, y, z);
    }

    // aplica una velocidad
    public void push(float vx, float vy, float vz) {
        vel2apply[0] += vx;
        vel2apply[1] += vy;
        vel2apply[2] += vz;
    }

    void resetLoc2Apply() {
        loc2apply[0] = loc2apply[1] = loc2apply[2] = 0;
    }

    public boolean isDinamic() {
        return espacios.size() > 0;
    }

    // Crea las variables base de OpenGL para mostrar espacios en modo debug
    public void createOpenGlVars() {
        float[] _lineCoords;    // Coordenadas del los extremos del cubo
        float[] _normalCoords;  // Normales
        float[] _boneValues;    // Que huesos afectan cada linea
        float[] _weightValues;  // Peso de los huesos

        int lineCount = espacios.size() * 12;   // 12 aristas por espacio

        _lineCoords = new float[lineCount * 2 * 3];  // lineas * 2 extremos * 3 coordenadas
        _normalCoords = new float[lineCount * 2 * 3];
        _boneValues = new float[lineCount * 2 * 2];   // 2 extremos * 2 influenciadores
        _weightValues = new float[lineCount * 2 * 2];

        for (int i = 0; i < espacios.size(); i++)   // espacios
        {
            bodySpace bs = espacios.get(i);
            int[] ejes=new int[3];

            // 4 vertices sin conexiones entre si (-1,-1,-1) (+1,-1,+1) (-1,+1,+1) (+1,+1,-1)
            for (int j = 0; j < 4; j++)   // Cada punto extremo
            {
                ejes[0] = (j >> 1 == 1) ? 1 : -1;
                ejes[1] = (j % 2 == 1) ? 1 : -1;
                ejes[2] = -ejes[0] * ejes[1];

                for (int k = 0; k < 3; k++)   // Cada dimension
                {
                    for (int l = 0; l < 2; l++)   // Cada extremo de la linea
                    {
                        int[] borde=new int[3];
                        float[] v1 = new float[4];
                        float[] v2 = new float[4];
                        int sign = l * 2 - 1;
                        int ubi = (((i * 4 + j) * 3 + k) * 2 + l) * 3;

                        v1[3]=1;
                        for(int d=0;d<3; d++) {
                            borde[d] = ejes[d] * (k == d ? sign : 1);   // +1 o -1
                            v1[d] = borde[d] * bs.radius;
                        }

                        if (bs.tipo == location.boneAtached)
                            Matrix.multiplyMV(v2, 0, mesh.huesos.get(bs.boneId).matriz, 0, v1, 0);
                        else
                            v2 = v1;

                        _lineCoords[ubi + 0] = v2[0];
                        _lineCoords[ubi + 1] = v2[1]+bs.yShift+bs.height*(borde[1]==1?1:0);
                        _lineCoords[ubi + 2] = v2[2];
                        _normalCoords[ubi + 0] = 0;    //0,1,0
                        _normalCoords[ubi + 1] = 1;    //0,1,0
                        _normalCoords[ubi + 2] = 0;    //0,1,0

                        if (mesh.skeleton) {
                            ubi = (((i * 4 + j) * 3 + k) * 2 + l) * 2;
                            if(bs.tipo == location.boneAtached) {
                                _boneValues[ubi + 0] = bs.boneId;
                                _weightValues[ubi + 0] = 1;
                            }
                            else
                            {
                                _boneValues[ubi + 0] = 0;
                                _weightValues[ubi + 0] = 0;
                            }
                            _boneValues[ubi + 1] = 0;
                            _weightValues[ubi + 1] = 0;
                        }
                    }
                }
            }
        }

        // initialize vertex byte buffer for shape coordinates
        ByteBuffer bb6 = ByteBuffer.allocateDirect(lineCount * 2 * 3 * 4); // 2 extremos, 3 coords, 4 del float
        bb6.order(ByteOrder.nativeOrder());

        // create a floating point buffer from the ByteBuffer
        _vertexBuffer = bb6.asFloatBuffer();
        _vertexBuffer.put(_lineCoords);
        _vertexBuffer.position(0);

        // initialize vertex byte buffer for shape coordinates
        ByteBuffer bb7 = ByteBuffer.allocateDirect(lineCount * 2 * 3 * 4);
        bb7.order(ByteOrder.nativeOrder());

        // create a floating point buffer from the ByteBuffer
        _normalBuffer = bb7.asFloatBuffer();
        _normalBuffer.put(_normalCoords);
        _normalBuffer.position(0);

        // initialize vertex byte buffer for shape coordinates
        ByteBuffer bb8 = ByteBuffer.allocateDirect(lineCount * 2 * 2 * 4); // 2 puntas, 2 huesos, 4 del float
        bb8.order(ByteOrder.nativeOrder());

        // create a floating point buffer from the ByteBuffer
        _boneBuffer = bb8.asFloatBuffer();
        _boneBuffer.put(_boneValues);
        _boneBuffer.position(0);

        // initialize vertex byte buffer for shape coordinates
        ByteBuffer bb9 = ByteBuffer.allocateDirect(lineCount * 2 * 2 * 4); // 2 puntas, 2 huesos, 4 del float
        bb9.order(ByteOrder.nativeOrder());

        // create a floating point buffer from the ByteBuffer
        _weightBuffer = bb9.asFloatBuffer();
        _weightBuffer.put(_weightValues);
        _weightBuffer.position(0);

        _vertexCount = lineCount * 2;
    }
}

class physics {
    public ArrayList<rigidFloor> pisos = new ArrayList<rigidFloor>();
    //private ArrayList<rigidBody> actores=new ArrayList<rigidBody>();;

    public void testPhisics(sceneManager sm) {
        rigidBody act1, act2;

        // aplico momentos y calculo colisiones con los pisos y paredes para cada uno
        Log.d("MyApp", "Move: testing");
        for (int i = 0; i < sm.scenePhoto.size(); i++) {
            entity e = sm.scenePhoto.get(i).entityCopy;
            if (e.isDinamic())
                testBody(sm.scenePhoto, i, sm.deltaTime());
        }

        //Log.d("MyApp", "Move: testing "+actores.size()+" actores");
        // evito colisiones entre los actores
        for (int i = 0; i < sm.scenePhoto.size(); i++) {
            entityPhoto ef1 = sm.scenePhoto.get(i);
            if (ef1.entityCopy.isDinamic()) {
                //act1 = (rigidBody) e1;
                for (int j = i + 1; j < sm.scenePhoto.size(); j++) {
                    entityPhoto ef2 = sm.scenePhoto.get(j);
                    if (ef2.entityCopy.isDinamic()) {
                        for (int k = 0; k < ((rigidBody) ef1.entityCopy).espacios.size(); k++) {
                            bodySpace be1 = ((rigidBody) ef1.entityCopy).espacios.get(k);
                            if(be1.collisionTest) {
                                for (int l = 0; l < ((rigidBody) ef2.entityCopy).espacios.size(); l++) {
                                    bodySpace be2 = ((rigidBody) ef2.entityCopy).espacios.get(l);
                                    if(be2.collisionTest) {
                                        float[] l1 = getSpaceLocation(sm.scenePhoto, i, k);
                                        float[] l2 = getSpaceLocation(sm.scenePhoto, j, l);
                                        if (distTest(l1, be1.radius, l2, be2.radius)) {
                                            float D = (float) Math.sqrt((l1[0] - l2[0]) * (l1[0] - l2[0]) + (l1[2] - l2[2]) * (l1[2] - l2[2]));
                                            float d = be1.radius + be2.radius - D;
                                            float q = d / D;
                                            Log.d("MyApp", "Move: d=" + d);
                                            // no toda la diferencia, solo la diferencia del borde (restar radios)
                                            ef1.entityCopy.addLocation(q * (l1[0] - l2[0]) / 2, 0, q * (l1[2] - l2[2]) / 2);
                                            ef2.entityCopy.addLocation(q * (l2[0] - l1[0]) / 2, 0, q * (l2[2] - l1[2]) / 2);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                ((rigidBody) ef1.entityCopy).resetLoc2Apply();
            }
        }
    }

    // ubicacion global de un espacio de colision
    float[] getSpaceLocation(List<entityPhoto> sf, int entityNumber, int spaceNumber) {
        float[] rl = new float[4];
        float[] tmp = new float[4];
        rigidBody rb = ((rigidBody) (sf.get(entityNumber).entityCopy));
        bodySpace bs = rb.espacios.get(spaceNumber);

        if (bs.tipo == location.boneAtached) {
            // posicion del hueso en rest, desde el root
            for (int i = 0; i < 3; i++)
                rl[i] = rb.mesh.huesos.get(bs.boneId).matriz[12 + i];

            rl[3] = 1;
            // pocision desde el root que le da la accion (accion es movimiento relativo al rest).
            Matrix.multiplyMV(tmp, 0, sf.get(entityNumber).skeletonCopy, 16 * bs.boneId, rl, 0);
            Matrix.multiplyMV(rl, 0, sf.get(entityNumber).entityCopy.getWorldMatrix(), 0, tmp, 0);
            rl[1] += bs.yShift;
        } else {
            rl = rb.getWorldLocation();
            rl[1] += rb.espacios.get(spaceNumber).yShift;
        }
        return rl;
    }

    // ojo que se puede correr mas de una vez por ciclo!!!
    void testBody(List<entityPhoto> sf, int entityNumber, float dt) {
        float[] rea = new float[3];
        rigidBody rb = (rigidBody) sf.get(entityNumber).entityCopy;
        float v = Math.abs(rb.vel[0]) + Math.abs(rb.vel[1]) + Math.abs(rb.vel[2]) + 0.001f;
        float maxDt = 0.1f;
        for (int i = 0; i < rb.espacios.size(); i++)
            maxDt = Math.min(maxDt, rb.espacios.get(i).radius * 0.9f / v);   // 90% del dadio como maximo

        int d = 0;
        while (dt > 0) {
            float partialTime = Math.min(dt, maxDt);
            rb.applyDt(partialTime);

            for (rigidFloor rf : pisos) {
                for (int i = 0; i < rb.espacios.size(); i++)
                    if (rb.espacios.get(i).collisionTest && rf.test(getSpaceLocation(sf, entityNumber, i), rb.espacios.get(i).radius, rea))
                        rb.applyReaction(rea[0], rea[1], rea[2], partialTime);
            }
            dt -= partialTime;
            d++;
        }
        Log.d("MyApp", "Passes: d=" + d);
    }

    // testea la colision siendo los dos objetos esferas de rardios RADIUS
    private boolean distTest(float l1[], float r1, float l2[], float r2) {
        float d[] = new float[3];
        for (int i = 0; i < 3; i++)
            d[i] = l1[i] - l2[i];

        // cheque rapido de distancia en plano horizontal
        if (Math.abs(d[0]) + Math.abs(d[2]) > 1.42f * (r1 + r2))
            return false;

        // cheque en eje vertical, lo hago primero porque es menos costoso que el ultimo
        if (l1[1] - r1 > l2[1] + r2 || l2[1] - r2 > l1[1] + r1)
            return false;

        return Math.sqrt(d[0] * d[0] + d[2] * d[2]) < (r1 + r2);
    }

    // dot product de vectores de 3 dimensiones
    float dot(float v1[], float v2[])
    {
        return v1[0]*v2[0]+v1[1]*v2[1]+v1[2]*v2[2];
    }

    // calcula la minima distancia entre dos valores. -1 es infinito
    public float minDistance( float a, float b)
    {
        if(a<0)
            return b;
        if(b<0)
            return a;
        return Math.min(a,b);
    }

    // x cuadrado
    float x2(float a)
    {
        return a*a;
    }

    // o=origen del rayo
    // u= direccion del rayo (modulo 1 !!!!)
    // c=centro de la esfera
    // r=radio de la esfera
    // https://en.wikipedia.org/wiki/Line-sphere_intersection

    // Devuelve la distancia a la que un rayo (sale desde o en direccion u) pega a una esfera (centro c y radio r)
    private float rayHitsSphere(float[] o, float[] u, float[] c, float r)
    {
        float[] oc=new float[3];
        for(int i=0;i<3;i++)
            oc[i]=o[i]-c[i];

        float uoc=dot(u,oc);
        float s=x2(uoc)-dot(oc,oc)+x2(r);
        // raiz es negativa => sin solucion => no pega en la esfera
        // uoc>0 intersecta en el segmento negativo del rayo => no pega adelante

        if(s<0 || uoc>0)
            return -1;

        float d=-uoc-(float)Math.sqrt(s);
        Log.d("MyApp", "Hit:("+d+") ["+c[1]+"]"+(o[0]+u[0]*d)+";"+(o[1]+u[1]*d)+";"+(o[2]+u[2]*d));

        return d;  // la otra solucion (+) es mas lejana ...
    }
    
    // Un rayo le pega a un espacio en particular
    // o es el centro del rayo y u la direccion UNITARIA
    public boolean testBodyRay(List<entityPhoto> sf, int entityNumber, float[] o, float[] u)
    {
        float distance=-1;
        rigidBody rb = (rigidBody) sf.get(entityNumber).entityCopy;
        for (int i = 0; i < rb.espacios.size(); i++) {
            bodySpace e = rb.espacios.get(i);
            if (e.rayTest)
            {
                float[] c = getSpaceLocation(sf, entityNumber, i);
                float d = rayHitsSphere(o, u, c, e.radius);
                if (d > 0)  // hay colision
                    distance = minDistance(distance, d);
            }
        }
        if(distance>0)
        {
            float floorD=testRay(o,u);
            if(floorD>0 && floorD<distance)
                return false;
            return true;
        }
        return false;
    }

    // Un rayo le pega a algun floor?
    // https://en.wikipedia.org/wiki/Line-plane_intersection
    public float testRay(float[] o, float[] u)
    {
        float distance=-1;
        float[] co=new float[3];
        for (rigidFloor rf : pisos)
        {
            for(int i=0; i<rf.count;i++)
            {
                rTriangle t=rf.bordes[i];
                float div=dot(t.nor, u);

                if(Math.abs(div)>0.00001)
                {
                    for(int j=0;j<3;j++)
                        co[j]=t.mp[j]-o[j];

                    float d=dot(co, t.nor)/div;
                    if(distance<0 || minDistance(distance,d)<distance)
                    {
                        float[] baryPos=new float[4];
                        float[] cartePos=new float[4];
                        for(int j=0;j<3;j++)
                            cartePos[j]=o[j]+u[j]*d;
                        cartePos[3]=1;
                        // Convierto a coordenadas barycentricas
                        Matrix.multiplyMV(baryPos,0,t.bMat,0,cartePos,0);

                        // fuera del triangulo o mas lejos que el radio
                        if(baryPos[0]>=0 && baryPos[1]>=0 && baryPos[0]+baryPos[1]<=1)
                            distance=minDistance(distance,d);
                    }
                }
            }
        }
        return distance;
    }
}