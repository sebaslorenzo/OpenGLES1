package com.example.admin.opengles1;

import android.opengl.Matrix;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Float.NaN;

enum enumSpaceType {fixedPosition, boneAttached};

class BodySpace
{
    enumSpaceType type;
    float radius;
    float yShift;
    int boneId;
    float height;
    boolean performCollisionTest;
    boolean performRayTest;

    public BodySpace(float radiusSize, float heightSize, float shiftVert, boolean collision, boolean ray)
    {
        type = enumSpaceType.fixedPosition;
        radius=radiusSize;
        yShift=shiftVert;
        height=heightSize;
        performCollisionTest =collision;
        performRayTest =ray;
    }

    public BodySpace(float radiusSize, float heightSize, int boneNumber, float shiftVert, boolean collision, boolean ray)
    {
        type = enumSpaceType.boneAttached;
        radius=radiusSize;
        boneId=boneNumber;
        yShift=shiftVert;
        height=heightSize;
        performCollisionTest =collision;
        performRayTest =ray;
    }
};


class Triangle
{
    public float[] vertex=new float[12];
    public float k;                         // termino constante del plano X*Nx+Y*Ny+Z*Nz=k
    public float[] bMat=new float[16];      // Matriz a modelo baricentrico
    public float[] normal=new float[3];     // normal a la superficie (normalizada)
    public float[] limInf=new float[3];     // limites inferior del cuerpo en el espacio
    public float[] limSup=new float[3];     // limites superior del cuerpo en el espacio

    public Triangle(float[] p1, float[] p2, float[] p3)
    {
        float[] v1=new float[3];
        float[] v2=new float[3];

        for(int i=0;i<3;i++)
        {
            vertex[  i]=p1[i];
            vertex[4+i]=p2[i];
            vertex[8+i]=p3[i];

            v1[i] = p2[i] - p1[i];    // v1=p2-p1
            v2[i] = p3[i] - p1[i];    // v2=p3-p1
            limInf[i]=Math.min(Math.min(p1[i],p2[i]),p3[i]); // calculo de limite inferior
            limSup[i]=Math.max(Math.max(p1[i],p2[i]),p3[i]);  // calculo de limite superior
        }
        vertex[3]=vertex[7]=vertex[11]=1;

        normal[0]=v1[1]*v2[2]-v1[2]*v2[1];
        normal[1]=v1[2]*v2[0]-v1[0]*v2[2];
        normal[2]=v1[0]*v2[1]-v1[1]*v2[0];
        double s=Math.sqrt(normal[0]* normal[0]+ normal[1]* normal[1]+ normal[2]* normal[2]);
        for(int i=0;i<3;i++)
            normal[i]=(float)(normal[i]/s);

        float[] mat=new float[16]; // Matriz de modelo baricentrico a cartesiano
        mat[0]=v1[0];   mat[4]=v2[0];   mat[8]= normal[0];  mat[12]=p1[0];
        mat[1]=v1[1];   mat[5]=v2[1];   mat[9]= normal[1];  mat[13]=p1[1];
        mat[2]=v1[2];   mat[6]=v2[2];   mat[10]= normal[2]; mat[14]=p1[2];
        mat[3]=0;       mat[7]=0;       mat[11]=0;      mat[15]=1;

        // Calculo matriz de cartesiano a barycentrico
        Matrix.invertM(bMat,0,mat,0);
        k= vertex[0]*normal[0]+ vertex[1]*normal[1]+ vertex[2]*normal[2];
    }

    public int vertexPosition(int i) { return i*4;}

    // calcula si una esfera que parte de origen y se mueve delta intersecta con un triangulo, parte interna
    // devuelve la distancia recorida (% de delta) o Float.MAX_VALUE si no colisiona
    public boolean getCollision2Triangle(int id, float[] origen, float[] delta, float radio, float dt, int cara)
    {
        // test de zona
        for (int i = 0; i < 3; i++) {
            if (Math.max(origen[i], origen[i] + delta[i]) + radio < limInf[i] || Math.min(origen[i], origen[i] + delta[i]) - radio > limSup[i])
                return false;   // Fuera de zona limite de influencia, primer chequeo
        }

        // (O+d.U+-r.N-C).N=0   O=origen, C=punto del plano, r=radio, d=incognita distancia, U=direccion del mov, N=normal)
        // se despeja d=-(O-C+-r.N).N / U.N

        // dir compensa la direccion arbitraria de la normal respecto del delta
        float dir=Math.signum(Mate.dot(normal,delta));
        float deltaNormal=Mate.dot(delta,Mate.multiplyVectorScalar(normal,dir));
        if( Math.abs(deltaNormal)<0.00001f )
            return false;   // movimiento paralelo al plano del triangulo?

        float ocn=Mate.dot(Mate.subsVectors(origen,vertex),Mate.multiplyVectorScalar(normal,dir));
        // d repesenta a cuantos deltas intersecta la esfera con el plano
        float d=-(ocn+radio) / deltaNormal;
        if(d>1) {
            Log.d("MyApp", "Crash: ;E=L;F="+cara+";O="+origen[0]+";"+origen[1]+";"+origen[2]+";D="+delta[0]+";"+delta[1]+";"+delta[2]+";d="+d);
            return false;   // no llego al plano
        }

        if(d<0) {  // intersecta pero yendo para atras, tengo que ver si estaba o no pasado del centro
            // d2 repesenta a cuantos deltas intersecta el centro de la esfera con el plano
            float d2 = -ocn / deltaNormal;
            if (d2 < 0) {
                Log.d("MyApp", "Crash: ;E=P;F=" + cara + ";O=" + origen[0] + ";" + origen[1] + ";" + origen[2] + ";D=" + delta[0] + ";" + delta[1] + ";" + delta[2] + ";d=" + d);
                return false;   // el centro ya estaba pasado del plano, prefiero que sega de largo
            }
        }

        float[] cartePos=new float[4];  // cartesiano
        float[] baryPos=new float[4];   // baricentrico?

        for(int i=0;i<3;i++)
            cartePos[i]=origen[i]+delta[i]*d;
        cartePos[3]=1;

        // Convierto a coordenadas barycentricas
        Matrix.multiplyMV(baryPos,0,bMat,0,cartePos,0);

        // QUe area estoy chequeando?
        int edge=-1;    // default es adentr del triangulo
        if(baryPos[0]<0)
            edge=2;
        else if(baryPos[0]+baryPos[1]>1)
            edge=1;
        if( baryPos[1]<0 )
            edge=0;

        // fuera del triangulo o mas lejos que el radio
        if(edge!=-1) {
            d=getCollisionDistance2Edge(id, origen, delta, radio, dt, edge);
            if( d==Float.MAX_VALUE) {
                Log.d("MyApp", "Crash: ;E="+edge+";F="+cara+";O="+origen[0]+";"+origen[1]+";"+origen[2]+";D="+delta[0]+";"+delta[1]+";"+delta[2]+";d="+d);
                return false;
            }
        }

        if(Math.abs(d)>1) {
            Log.d("MyApp", "Crash: ;E="+edge+";F="+cara+";O="+origen[0]+";"+origen[1]+";"+origen[2]+";D="+delta[0]+";"+delta[1]+";"+delta[2]+";d="+d);
            return false;   // se aleja o no llego al plano
        }

        for (int i = 0; i < 3; i++)
            delta[i] -= dir * normal[i] * (1 - d) * deltaNormal;

        Log.d("MyApp", "Crash: ;E="+edge+";F="+cara+";O="+origen[0]+";"+origen[1]+";"+origen[2]+";D="+delta[0]+";"+delta[1]+";"+delta[2]+";d="+d);
        Log.d("MyApp", "Test: ID="+id+";Edge="+edge+";Origen="+origen[1]+";Delta="+delta[0]+";"+delta[1]+";"+delta[2]+";Normal="+normal[1]+";DN="+deltaNormal+";Plano="+ vertex[1]+";radio="+radio+";d="+d);

        return true;
    }

    // calcula si una esfera que parte de origen y se mueve delta intersecta con un triangulo, parte interna
    // devuelve la distancia recorida (% de delta) o Float.MAX_VALUE si no colisiona
    public float getCollisionDistance2Edge(int id, float[] origen, float[] delta, float radio, float dt, int edgeId)
    {
        float[] edge=new float[4];
        float[] p0=new float[4];
        float[] p1=new float[4];

        edge[3]=p0[3]=1;
        for(int i=0; i<3; i++) {
            p0[i] = vertex[vertexPosition(edgeId) + i];
            p1[i] = vertex[vertexPosition((edgeId+1)%3) + i];
            edge[i] = p1[i] - p0[i];
        }

        float edgeSqrLen=Mate.dot(edge,edge);
        float dotDeltaEdge=Mate.dot(delta,edge);

        // Proyecciones del movimiento y del punto 0 sobre un plano perpendicular al edge que pasa por origen
        float[] pDelta=Mate.subsVectors(delta,Mate.multiplyVectorScalar(edge,dotDeltaEdge/edgeSqrLen));
        float[] pP0=Mate.subsVectors(p0,Mate.multiplyVectorScalar(edge,Mate.dot(Mate.subsVectors(p0,origen),edge)/edgeSqrLen));

        float A=Mate.dot(pDelta, pDelta);
        float B=2*Mate.dot(pDelta, Mate.subsVectors(origen,pP0));
        float[] cpp=Mate.subsVectors(origen,pP0);
        float C=Mate.dot(cpp, cpp)-radio*radio;

        if(B>0 || Mate.nearZero(A)) // Se aleja o no hay delta
            return Float.MAX_VALUE;

        // d calculada en la Proyeccion sobre el plano
        float pd=Mate.minCuadraticRoot(A,B,C);

        if(Float.isNaN(pd))  // No hay solución
            return Float.MAX_VALUE;

        // Que significa que la raiz sea negativa?
        //float d=(float)(pd*Math.sqrt(1+dotDeltaEdge/(edgeSqrLen*Math.sqrt(A))));
        float d=(float)(pd*Math.sqrt(Mate.dot(delta,delta)));
        if(Float.isNaN(d))  // what????
            return Float.MAX_VALUE;

        float[] p=Mate.addVectors(origen,Mate.multiplyVectorScalar(delta,d));

        if(Mate.dot(Mate.subsVectors(p,p0),edge)<0)
            return Float.MAX_VALUE;

        if(Mate.dot(Mate.subsVectors(p,p1),edge)>0)
            return Float.MAX_VALUE;

        Log.d("MyApp", "TestEdge: ID="+id+";d="+d);

        return d;
    }
}

class RigidFloor
{
    public Triangle[] bordes;
    public int count;
    public float[] limInf;  // limites inferior del cuerpo en el espacio
    public float[] limSup;  // limites superior del cuerpo en el espacio

    public RigidFloor(Entity e)
    {
        float[] mat = e.getWorldMatrixClone();
        float[] p1=new float[4];
        float[] p2=new float[4];
        float[] p3=new float[4];
        limInf=new float[3];
        limSup=new float[3];

        count=e.mesh.totalFaceCount;
        bordes=new Triangle[count];

        int j=0;

        for( SubMesh sr: e.mesh.subReticula) {
            for (int i = 0; i < count; i++) {
                transform(p1, mat, e.mesh.vertices.get(sr.caras.get(i).v1).co);
                transform(p2, mat, e.mesh.vertices.get(sr.caras.get(i).v2).co);
                transform(p3, mat, e.mesh.vertices.get(sr.caras.get(i).v3).co);
                bordes[j] = new Triangle(p1, p2, p3);
                for (int z = 0; z < 3; z++) {
                    limInf[z] = Math.min(limInf[z], bordes[i].limInf[z]); // calculo de limite inferior
                    limSup[z] = Math.max(limSup[z], bordes[i].limSup[z]);  // calculo de limite superior
                }
                j++;
            }
        }
    }

    // calcula algun tringulo intersecta con una esfera y devuelve cuanto deberia moverse para evitarlo
    // origen: obicacion original de la esfera
    // delta: cuant se deberia mover
    public boolean test(int id, float[] origen, float[] delta, float radio, float dt)
    {
        // Fuera de zona limite de influencia general, primer chequeo
        for(int i=0;i<3;i++)
            if(Math.max(origen[i],origen[i]+delta[i])+radio<limInf[i] || Math.min(origen[i],origen[i]+delta[i])-radio>limSup[i])
                return false;

        boolean collision=false;

        for(int i=0; i<count;i++)
            collision|=bordes[i].getCollision2Triangle(id, origen, delta, radio, dt, i);


        return collision;
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

class RigidBody extends Entity {
    public float[] vel;
    public float[] vel2apply;
    public float[] loc2apply;
    //float radius;
    //float vertShift;
    public boolean collision;
    List<BodySpace> espacios = new ArrayList<BodySpace>();

    // variables para debugear
    public int _vertexCount;
    public FloatBuffer _vertexBuffer;
    public FloatBuffer _normalBuffer;
    public FloatBuffer _boneBuffer;
    public FloatBuffer _weightBuffer;
    public boolean OGL_Calc=false;  // para calcular os datos la primera vez que los necesito

    public RigidBody(String meshName) {
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
        espacios.add(new BodySpace(radiusSize, heightSize, shiftVert, collision, ray));
    }

    // crea una esfera ligada a un hueso en particular
    void addBoneRelatedSpace(float radiusSize, float heightSize, String boneName, float shiftVert, boolean collision, boolean ray) {
        if (mesh.skeleton) {
            int b = mesh.getBoneIdByName(boneName);
            if (b >= 0)
                espacios.add(new BodySpace(radiusSize, heightSize, b, shiftVert, collision, ray));
        }
    }

    // ahora es seguro hacer la destruccion?
    public void destroy() {
        super.destroy();
    }

    // calcula el momento y la gravedad del objeto antes de colisionar
    public void calcPysics(float dt, float[] dest) {
        // Velocidad aplicada al cuerpo
        vel[0] += vel2apply[0];
        if (vel2apply[1] > 0)
            vel[1] += vel2apply[1];
        vel[2] += vel2apply[2];
        vel2apply[0] = vel2apply[1] = vel2apply[2] = 0;

        // Gravedad
        vel[1] -= 9.8f * dt;
        for(int i=0;i<3;i++)
            dest[i]=vel[i]*dt+loc2apply[i];
        Log.d("MyApp", "vel=" + vel[1] + " Vel2apply=" + vel2apply[1] + " dt=" + dt);
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
            if (Math.abs(y) / s > 0.7) // cos inclinacion maxima => menos angulo de inclinacion => freno todo
                vel[0] = vel[1] = vel[2] = 0; // saque vel1 para poder saltar
            else {
                vel[0] += d * x / s; //vel[0]*=(1-dt);
                vel[1] += d * y / s; //vel[1]*=(1-dt);
                vel[2] += d * z / s; //vel[2]*=(1-dt);
            }
        }
        vel[0] = vel[1] = vel[2] = 0; // saque vel1 para poder saltar
        collision = true;
        Log.d("MyApp", "Rigid:" + dt + ":" + x + "," + y + "," + z+ "," + vel[0]+ "," + vel[1]+ "," + vel[2]);
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
            BodySpace bs = espacios.get(i);
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

                        if (bs.type == enumSpaceType.boneAttached)
                            Matrix.multiplyMV(v2, 0, mesh.huesos.get(bs.boneId).restMatrixFromRoot, 0, v1, 0);
                        else
                            v2 = v1;

                        _lineCoords[ubi    ] = v2[0];
                        _lineCoords[ubi + 1] = v2[1]+bs.yShift+bs.height*(borde[1]==1?1:0);
                        _lineCoords[ubi + 2] = v2[2];
                        _normalCoords[ubi    ] = 0;    //0,1,0
                        _normalCoords[ubi + 1] = 1;    //0,1,0
                        _normalCoords[ubi + 2] = 0;    //0,1,0

                        if (mesh.skeleton) {
                            ubi = (((i * 4 + j) * 3 + k) * 2 + l) * 2;
                            if(bs.type == enumSpaceType.boneAttached) {
                                _boneValues[ubi] = bs.boneId;
                                _weightValues[ubi] = 1;
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

class Physics {
    public ArrayList<RigidFloor> pisos = new ArrayList<RigidFloor>();

    // Prmero aplico los movimientos de los personajes, la inercia que tenian y la grabedad
    // Despues detecto las colisiones entre los objetos dinamicos
    // Finalmente veo que no hayan atravezado pardes
    // los pasos 1 y 2 se hacen sobre REA de manera de conocer la ubicacion original
    public void testPhisics(SceneManager sm) {
        float dt=sm.deltaTime();

        // aplico momentos, movimientos y gravedad
        Log.d("MyApp", "Move: moving");
        for( EntityPhoto ef : sm.scenePhoto )
            if (ef.entityCopy.isDinamic())
                ((RigidBody)(ef.entityCopy)).calcPysics(dt, ef.rea);

        //Log.d("MyApp", "Move: testing "+actores.size()+" actores");
        // evito colisiones entre los actores
        for (int i = 0; i < sm.scenePhoto.size(); i++) {
            EntityPhoto ef1 = sm.scenePhoto.get(i);
            if (ef1.entityCopy.isDinamic()) {
                //act1 = (rigidBody) e1;
                for (int j = i + 1; j < sm.scenePhoto.size(); j++) {
                    EntityPhoto ef2 = sm.scenePhoto.get(j);
                    if (ef2.entityCopy.isDinamic()) {
                        for (int k = 0; k < ((RigidBody) ef1.entityCopy).espacios.size(); k++) {
                            BodySpace be1 = ((RigidBody) ef1.entityCopy).espacios.get(k);
                            if(be1.performCollisionTest) {
                                for (int l = 0; l < ((RigidBody) ef2.entityCopy).espacios.size(); l++) {
                                    BodySpace be2 = ((RigidBody) ef2.entityCopy).espacios.get(l);
                                    if(be2.performCollisionTest) {
                                        float[] l1 = Mate.addVectors(getSpaceLocationClone(sm.scenePhoto, i, k), ef1.rea);
                                        float[] l2 = Mate.addVectors(getSpaceLocationClone(sm.scenePhoto, j, l), ef2.rea);
                                        if (distTest(l1, be1.radius, l2, be2.radius)) {
                                            float D = (float) Math.sqrt((l1[0] - l2[0]) * (l1[0] - l2[0]) + (l1[2] - l2[2]) * (l1[2] - l2[2]));
                                            float d = be1.radius + be2.radius - D;
                                            float q = d / D;
                                            Log.d("MyApp", "Move: d=" + d);
                                            // no toda la diferencia, solo la diferencia del borde (restar radios)
                                            ef1.rea[0]+=q * (l1[0] - l2[0]) / 2;
                                            ef1.rea[1]+=q * (l1[1] - l2[1]) / 2;
                                            ef1.rea[2]+=q * (l1[2] - l2[2]) / 2;
                                            ef2.rea[0]+=q * (l2[0] - l1[0]) / 2;
                                            ef2.rea[1]+=q * (l2[1] - l1[1]) / 2;
                                            ef2.rea[2]+=q * (l2[2] - l1[2]) / 2;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                ((RigidBody) ef1.entityCopy).resetLoc2Apply();
            }
        }

        // calculo colisiones con los pisos y paredes para cada uno
        Log.d("MyApp", "Move: testing2");

        for (int i = 0; i < sm.scenePhoto.size(); i++) {
            Entity e = sm.scenePhoto.get(i).entityCopy;
            if (e.isDinamic())
                testBody(sm.scenePhoto, i, sm.deltaTime(),sm.scenePhoto.get(i).rea);
        }
    }

    // ubicacion global de un espacio de colision
    float[] getSpaceLocationClone(List<EntityPhoto> sf, int entityNumber, int spaceNumber) {
        float[] rl = new float[4];
        float[] tmp = new float[4];
        RigidBody rb = ((RigidBody) (sf.get(entityNumber).entityCopy));
        BodySpace bs = rb.espacios.get(spaceNumber);

        if (bs.type == enumSpaceType.boneAttached) {
            // posicion del hueso en rest, desde el root
            System.arraycopy(rb.mesh.huesos.get(bs.boneId).restMatrixFromRoot, 12, rl, 0, 3);

            rl[3] = 1;
            // pocision desde el root que le da la accion (accion es movimiento relativo al rest).
            Matrix.multiplyMV(tmp, 0, sf.get(entityNumber).skeletonCopy, 16 * bs.boneId, rl, 0);
            Matrix.multiplyMV(rl, 0, sf.get(entityNumber).entityCopy.getWorldMatrixClone(), 0, tmp, 0);
            rl[1] += bs.yShift;
        } else {
            rl = rb.getWorldLocationClone();
            rl[1] += rb.espacios.get(spaceNumber).yShift;
        }
        return rl;
    }

    // ojo que se puede correr mas de una vez por ciclo!!!
    void testBody(List<EntityPhoto> sf, int entityNumber, float dt, float[] rea) {
        RigidBody rb = (RigidBody) sf.get(entityNumber).entityCopy;

        for (RigidFloor rf : pisos) {
            for (int i = 0; i < rb.espacios.size(); i++) {
                if (rb.espacios.get(i).performCollisionTest && rf.test(rb.id, getSpaceLocationClone(sf, entityNumber, i), rea, rb.espacios.get(i).radius, dt))
                    rb.collision = true;
            }
        }

        float[] loc=rb.getWorldLocationClone();
        Log.d("MyApp", "testBody: ID="+rb.id+";Collision="+rb.collision+";Loc="+loc[0]+";"+loc[1]+";"+loc[2]+";Delta="+rea[0]+";"+rea[1]+";"+rea[2]+";");

        if(!rb.collision)
            rb.addLocation(rea[0], rea[1], rea[2]);
        else
            rb.applyReaction(rea[0], rea[1], rea[2], dt);
    }

    // testea la colision siendo los dos objetos esferas de rardios RADIUS
    private boolean distTest(float[] l1, float r1, float[] l2, float r2) {
        float[] d = new float[3];
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
    public static float dota(float[] v1, float[] v2)
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

        float uoc=Mate.dot(u,oc);
        float s=x2(uoc)-Mate.dot(oc,oc)+x2(r);
        // raiz es negativa => sin solucion => no pega en la esfera
        // uoc>0 intersecta en el segmento negativo del rayo => no pega adelante

        if(s<0 || uoc>0)
            return -1;

        float d=-uoc-(float)Math.sqrt(s);
        Log.d("MyApp", "Hit:("+d+") ["+c[1]+"]"+(o[0]+u[0]*d)+";"+(o[1]+u[1]*d)+";"+(o[2]+u[2]*d));

        return d;  // la otra solucion (+) es mas lejana ...
    }

    // Devuelve la distancia a la que un rayo (sale desde o en direccion u) pega a un cilindro (centro c, altura h y radio r)
    private float rayHitsCylinder(float[] o, float[] u, float[] c, float h, float r)
    {
        float OCx=o[0]-c[0];
        float OCz=o[2]-c[2];
        float A=u[0]*u[0]+u[2]*u[2];
        float B=2*(u[0]*OCx+u[2]*OCz);
        float C=OCx*OCx+OCz*OCz-r*r;
        float D=B*B-4*A*C;

        // raiz es negativa => sin solucion => no pega en la esfera
        // B>0 intersecta en el segmento negativo del rayo => no pega adelante
        if(D<0 || B>0)
            return -1;

        float d=(-B-(float)Math.sqrt(D))/(2*A);

        float hz=o[1]+u[1]*d-c[1];
        // pega fuera del vertical propio del cilindro
        if(hz<0 || hz>Math.max(h,r))
            return -1;

        Log.d("MyApp", "Hit:("+d+") ["+c[1]+"]"+(o[0]+u[0]*d)+";"+(o[1]+u[1]*d)+";"+(o[2]+u[2]*d));
        return d;  // la otra solucion (+) es mas lejana ...
    }

    // Un rayo le pega a un espacio en particular
    // o es el centro del rayo y u la direccion UNITARIA
    public boolean testBodyRay(List<EntityPhoto> sf, int entityNumber, float[] o, float[] u, float[] retUbi)
    {
        float distance=-1;
        RigidBody rb = (RigidBody) sf.get(entityNumber).entityCopy;
        for (int i = 0; i < rb.espacios.size(); i++) {
            BodySpace e = rb.espacios.get(i);
            if (e.performRayTest)
            {
                float[] c = getSpaceLocationClone(sf, entityNumber, i);
                float d = rayHitsCylinder(o, u, c, e.height, e.radius);
                if (d > 0)  // hay colision
                    distance = minDistance(distance, d);
            }
        }
        if(distance>0)
        {
            float floorD=testRay(o,u);
            if(floorD>0 && floorD<distance)
                return false;

            retUbi[0]=o[0]+distance*u[0];
            retUbi[1]=o[1]+distance*u[1];
            retUbi[2]=o[2]+distance*u[2];

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
        for (RigidFloor rf : pisos)
        {
            for(int i=0; i<rf.count;i++)
            {
                Triangle t=rf.bordes[i];
                float div=Mate.dot(t.normal, u);

                if(Math.abs(div)>0.00001)
                {
                    for(int j=0;j<3;j++)
                        co[j]=t.vertex[j]-o[j];

                    float d=Mate.dot(co, t.normal)/div;
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