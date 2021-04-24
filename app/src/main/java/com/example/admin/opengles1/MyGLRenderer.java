package com.example.admin.opengles1;
import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;
import android.view.MotionEvent;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MyGLRenderer implements GLSurfaceView.Renderer {   // extends touchDev
    private Camera xcam=null;
    private Camera followCam;
    private SceneManager scene;
    private Puppet ent;
    private Robot rob;
    private RigidBody cubeEnt;
    private Entity luz;
    private Entity punt;
    private long ultimo=System.currentTimeMillis();
    RigidFloor rf;
    private Context context;

    public MyGLRenderer(Context theContext)
    {
        context=theContext;
    }

    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        // Set the background frame color
        GLES20.glClearColor(0.5f, 1.0f, 1.0f, 1.0f);

        Ambiente.getInstance().setContext(context);
        xcam=new Camera();
        followCam=new Camera();
        scene=new SceneManager(xcam);
        scene.enablePhisics();
        scene.setLightSource(20,100,20);

        xcam.setLocation(0,3,-10.0f);
        xcam.lookAt(new float[] {0,0,1.0f},new float[]{0,1.0f,0});
        xcam.setParent(scene.root);

        ent=new Puppet("minecraft");
        //ent.addFixedSpace(0.4f,2,3,false,true);
        ent.addFixedSpace(0.5f,0f,0.5f,true,false);
        //ent.addBoneRelatedSpace(0.1f, 0.1f,"ball_r",0.09f, true, false);
        //ent.addBoneRelatedSpace(0.1f, 0.1f, "ball_l",0.09f, true, false);
        ent.debug=true; ent.color[0]=1.0f;ent.color[1]=0.0f;ent.color[2]=0.0f;
        ent.setParent(scene.root);
        ent.setId(2);
/*
        rob=new Robot("minecraft");
        rob.addFixedSpace(0.5f,0f,0.5f,true,false);
        rob.addBoneRelatedSpace(0.4f, 1.2f,"spine_01",0.0f, false, true);
        rob.addBoneRelatedSpace(0.3f, 0.5f,"head",0.0f, false, true);
        //rob.addBoneRelatedSpace(0.1f, 0.1f,"ball_r",0.09f, true, false);
        //rob.addBoneRelatedSpace(0.1f, 0.1f, "ball_l",0.09f, true, false);
        rob.debug=true; rob.color[0]=1.0f;rob.color[1]=0.0f;rob.color[2]=0.0f;
        rob.setParent(scene.root);
        rob.setLocation(3,0,10);
        rob.setId(1);
*/

        followCam.setLocation(0,5,-5.0f);
        followCam.lookAt(new float[] {0,2.0f,1.0f},new float[]{0,1.0f,0});
        followCam.setParent(ent);

        Entity c2=new Entity("Plane");
        c2.color[0]=0;c2.color[2]=0;
        c2.setLocation(0,-1,0);
        c2.setParent(scene.root);
/*
        Entity c3=new Entity("Tri");
        c3.color[0]=0;c3.color[2]=0;
        c3.setLocation(0,-2,0);
        c3.setParent(scene.root);
*/
        luz=new Entity("piramid");
        luz.color[0]=0;luz.color[2]=0;
        luz.setLocation(0,5,0);
        luz.setParent(scene.root);

        punt=new Entity("puntero");
        punt.color[0]=0;punt.color[1]=1;punt.color[2]=0;
        punt.setLocation(0,6,0);
        punt.setParent(scene.root);

        rf=new RigidFloor(c2);
        scene.attachToPhisics(rf);
/*
        RigidFloor rf3=new RigidFloor(c3);
        scene.attachToPhisics(rf3);
*/
        cubeEnt=new RigidBody("piramid");
        cubeEnt.addFixedSpace(1.0f,0,0.0f, true, false);
        cubeEnt.debug=true; cubeEnt.color[0]=0.0f;cubeEnt.color[1]=0.0f;
        cubeEnt.setParent(scene.root);
        cubeEnt.setLocation(3.1f,5.0f,1.0f);   // 1 5 1
        cubeEnt.setId(3);

        touchManager tm=touchManager.getInstance();
        tm.addField(1,0,50,0,100);
        tm.addField(2,50,100,0,100);

        scene.resetTime();
    }

    public void onDrawFrame(GL10 unused) {
        boolean vMover=false;
        boolean following=(scene.getViewPort()==followCam);
        float vAng=0;
        float deltaT=scene.updateDeltaTime();
        double s=0;
        float Tx=0;
        float Ty=0;
        long t0=System.currentTimeMillis();
        touchManager tm=touchManager.getInstance();

        float a=(System.currentTimeMillis()-ultimo)/600.0f;
        float b=20.0f*(float)Math.cos(a);
        Log.d("MyApp", "Luz: "+a+":"+b);

        scene.setLightSource(20.0f*(float)Math.cos(a),5,20.0f*(float)Math.sin(a));
        luz.setLocation(20.0f*(float)Math.cos(a),5,20.0f*(float)Math.sin(a));
        scene.draw();   // rutina donde se lanza el dibujo de cada objeto del arbol

        long t1=System.currentTimeMillis();

        touchField tf=tm.getField(2);
        if(tf.pressed) {
            Tx = tf.deltaX(1);
            Ty = tf.deltaY(1);
            if(following) {
                Ty=Math.min(Ty,0);
                tf.resetInitialX();
            }
            s = Math.sqrt(Tx * Tx + Ty * Ty);
        }

        if(tf.pressed && s>5)
        {
            vMover=true;
            Tx = (float) (Tx / s);
            Ty = (float) (Ty / s);
            if(following)
            {
                vAng = -(float) Math.atan2((double) Tx, (double) -Ty) / 5;
                //vAng=(Math.abs(vAng)<0.07)?0.0f:vAng-0.07f*Math.signum(vAng);
            }
            else
                vAng = ent.goToAngle(SceneManager.ejeZ, Tx, Ty, scene.getViewPort());
            Log.d("MyApp", "Angulo2: "+vAng+":"+Tx+":"+Ty);
        }

        long t2=System.currentTimeMillis();

        ent.makeItAlive(vMover, vAng, tm.getField(1).getClick(), deltaT);
/*
        rob.makeItAlive(deltaT);
        rob.testShot(deltaT, punt);
*/
        long t3=System.currentTimeMillis();

        // aplica el movimiento newtoniano a cada objeto
        // testea las colisiones y reacciona en cada caso
        if(scene.phy!=null)
            scene.phy.testPhisics(scene);

        long t4=System.currentTimeMillis();
        Log.d("MyApp", "Time: "+(t1-t0)+":"+(t2-t1)+":"+(t3-t2)+":"+(t4-t3));

        if(tm.getField(2).getDClick())
        {
            if(!following)
                scene.setViewPort(followCam);
            else
                scene.setViewPort(xcam);
        }
    }

    public void onTouchEvent(MotionEvent e)
    {
        touchManager.getInstance().onTouchEvent(e);
    }
        //@Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        GLES20.glViewport(0, 0, width, height);

        float ratio = (float) width / height;

        Log.d("MyApp", "Surface "+ width +" "+ height);

        // this projection matrix is applied to object coordinates
        // in the onDrawFrame() method
        Matrix.frustumM(scene.mProjectionMatrix, 0, -ratio, ratio, -1, 1, 2, 50);
        //scene.getViewPort().calcViewMatrix();
        Ambiente.getInstance().displayh=height;
        Ambiente.getInstance().displayw=width;
    }

    public void rotar(float dx, float dy) {
        scene.getViewPort().pitch(-dy/40);
        scene.getViewPort().yaw(-dx/40);
    }

    public void girar(float dx) {
        ent.yaw(dx/20);
    }

    public void caminar(float dx, float dy) {
        scene.getViewPort().addLocation(-dx/20,0,-dy/20);
    }

    public void volar(float dx, float dy) {
        scene.getViewPort().addLocation(-dx/20,-dy/20, 0);
    }
/*
    public void arriba() {
        ent.debug=!ent.debug;
        soundManager.getInstance().playSound("step.wav",1,1);
        ent.playAction("arriba",mode.boomerang,1,0);
        //Log.d("MyApp", ">arriba");
    }

    public void xabajo() {
        ent.playAction("abajo",mode.boomerang,1,0);
        //Log.d("MyApp", ">abajo");
    }

    public void izquierda() {
        ent.playAction("Izquierda",mode.boomerang,1,0);
        //Log.d("MyApp", ">izquierda");
    }

    public void derecha() {
        ent.playAction("derecha",mode.boomerang,1,0);
        //Log.d("MyApp", ">derecha");
    }
*/
}