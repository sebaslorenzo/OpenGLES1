package com.example.admin.opengles1;

import android.opengl.Matrix;
import android.util.Log;

import java.util.ArrayList;

class rTriangle
{
    public float[] mp=new float[3];      // main point, no se para que lo guardo
    public float[] bMat=new float[16];   // Matriz a modelo baricentrico
    public float[] nor=new float[3];     // normal a la superficie (normalizada)
    public float[] limInf=new float[3];  // limites inferior del cuerpo en el espacio
    public float[] limSup=new float[3];  // limites superior del cuerpo en el espacio

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
        }

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

class rigidBody extends entity
{
    public float[] vel;
    public float[] vel2apply;
    public float[] loc2apply;
    float radius;
    float vertShift;
    public boolean collision;

    public rigidBody(String meshName, float vRadius, float shift)
    {
        super(meshName);
        vel=new float[3];
        vel[0]=vel[1]=vel[2]=0;
        vel2apply=new float[3];
        vel2apply[0]=vel2apply[1]=vel2apply[2]=0;
        loc2apply=new float[3];
        loc2apply[0]=loc2apply[1]=loc2apply[2]=0;
        radius=vRadius;
        vertShift=shift;
        collision=false;
    }

    // ahora es seguro hacer la destruccion?
    public void destroy()
    {
        super.destroy();
    }

    public void applyDt(float dt)
    {
        vel[0]+=vel2apply[0];
        if(vel2apply[1]>0)
            vel[1]+=vel2apply[1];
        vel[2]+=vel2apply[2];
        vel2apply[0]=vel2apply[1]=vel2apply[2]=0;

        vel[1]-=9.8f*dt;   // aceleracion
        addLocation(vel[0]*dt,vel[1]*dt,vel[2]*dt); // velocidad
        addLocation(loc2apply[0],loc2apply[1],loc2apply[2]); // moviminto fijo
        Log.d("MyApp", "vel="+ vel[1] +" dt="+ dt);
        Log.d("MyApp", "Move:loc2apply="+ loc2apply[0] +" ; "+ loc2apply[2]);
        collision=false;
    }

    public void applyReaction(float x, float y, float z, float dt)
    {
        Log.d("MyApp", "Reaccion="+ x+":"+y+":"+z);
        // Posicion
        addLocation(x, y, z);
        // Velocidad
        float s=(float)Math.sqrt(x*x+y*y+z*z);
        if(s>0.001) {
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
        collision=true;
        Log.d("MyApp", "Rigid:" + String.valueOf(dt)+":"+String.valueOf(x)+","+ String.valueOf(y)+","+ String.valueOf(z));
    }

    // ademas de moverlo lo frena
    public void move( float x, float y, float z)
    {
        loc2apply[0]+=x;
        loc2apply[1]+=y;
        loc2apply[2]+=z;
        //vel[0]=vel[2]=0;
        //addLocation(x, y, z);
    }

    // aplica una velocidad
    public void push( float vx, float vy, float vz)
    {
        vel2apply[0]+=vx;
        vel2apply[1]+=vy;
        vel2apply[2]+=vz;
    }

    void resetLoc2Apply() {loc2apply[0]=loc2apply[1]=loc2apply[2]=0;}

    public boolean isDinamic()   {return true;}
}

class physics
{
    public ArrayList<rigidFloor> pisos=new ArrayList<rigidFloor>();
    private ArrayList<rigidBody> actores=new ArrayList<rigidBody>();;

    public void testPhisics(sceneManager sm)
    {
        // aplico momentos y calculo colisiones con los pisos y paredes para cada uno
        Log.d("MyApp", "Move: testing");
        testNode(sm.root, sm.deltaTime());
        actores.clear();
        addObjects(sm.root);
        Log.d("MyApp", "Move: testing "+actores.size()+" actores");
        // evito colisiones entre los actores
        for(int i=0; i<actores.size();i++) {
            for(int j=i+1;j<actores.size(); j++)
            {
                rigidBody act1=actores.get(i);
                rigidBody act2=actores.get(j);
                float[] l1=act1.getLocation();
                float[] l2=act2.getLocation();
                if(distTest(act1,act2))
                {
                    float D=(float)Math.sqrt((l1[0]-l2[0])*(l1[0]-l2[0])+(l1[2]-l2[2])*(l1[2]-l2[2]));
                    float d=actores.get(i).radius+actores.get(j).radius-D;
                    float q=d/D;
                    Log.d("MyApp", "Move: d="+d);
                    // no toda la diferencia, solo la diferencia del borde (restar radios)
                    actores.get(i).addLocation(q*(l1[0]-l2[0])/2, 0,q*(l1[2]-l2[2])/2);
                    actores.get(j).addLocation(q*(l2[0]-l1[0])/2, 0,q*(l2[2]-l1[2])/2);
                }
            }
            actores.get(i).resetLoc2Apply();
        }
    }

    // lista en actores todos los objetos dinamicos
    void addObjects(node n) {
        for (sceneable c : n.children) {
            if (c.isEnabled() && c.isDinamic())
                actores.add((rigidBody) c);
            if (c.isParent())
                addObjects((node) c);
        }
    }

    // ojo que se puede correr mas de una vez por ciclo!!!
    void testNode(node n, float dt)
    {
        float maxDt=0.1f;
        while(dt>maxDt)
        {
            testNode(n, maxDt);
            dt-=maxDt;
        }

        float[] rea=new float[3];
        for (sceneable c : n.children) {
            if( c.isEnabled()) {
                if (c.isDinamic()) {    // aplico gravedad y la velocidad
                    ((rigidBody) c).applyDt(dt);

                    for (rigidFloor rf : pisos) {
                        //for (int i = 0; i < 3; i++) rea[i] = 0;
                        float[] centro = c.getLocation();
                        centro[1] += ((rigidBody) c).vertShift;

                        if (rf.test(centro, ((rigidBody) c).radius, rea))
                            ((rigidBody) c).applyReaction(rea[0], rea[1], rea[2], dt);
                    }
                }
                if (c.isParent())
                    testNode((node) c, dt);
            }
        }
    }

    // testea la colision siendo los dos objetos cilindros de rardios y altura RADIUS
    private boolean distTest( rigidBody rb1, rigidBody rb2)
    {
        float l1[]=rb1.getLocation();
        float l2[]=rb2.getLocation();
        float d[]=new float[3];
        for(int i=0; i<3; i++)
            d[i]=l1[i]-l2[i];

        // cheque rapido de distancia en plano horizontal
        if(Math.abs(d[0])+Math.abs(d[2])>1.42f*(rb1.radius+rb2.radius))
            return false;

        // cheque en eje vertical, lo hago primero porque es menos costoso que el ultimo
        if(l1[1]-rb1.radius>l2[1]+rb2.radius || l2[1]-rb2.radius>l1[1]+rb1.radius)
            return false;

        return Math.sqrt(d[0]*d[0]+d[2]*d[2])<(rb1.radius+rb2.radius);
    }
}