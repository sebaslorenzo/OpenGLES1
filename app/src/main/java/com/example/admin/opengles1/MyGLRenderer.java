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
    private camera xcam=null;
    private camera followCam;
    private sceneManager scene;
    private puppet ent;
    private rigidBody cubeEnt;
    private entity luz;
    private long ultimo=System.currentTimeMillis();
    rigidFloor rf;
    private Context context;

    public MyGLRenderer(Context theContext)
    {
        context=theContext;
    }

    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        // Set the background frame color
        GLES20.glClearColor(0.5f, 1.0f, 1.0f, 1.0f);

        ambiente.getInstance().setContext(context);
        xcam=new camera();
        followCam=new camera();
        scene=new sceneManager(xcam);
        scene.enablePhisics();
        scene.setLightSource(20,100,20);

        xcam.setLocation(0,3,-10.0f);
        xcam.lookAt(new float[] {0,0,1.0f},new float[]{0,1.0f,0});
        xcam.setParent(scene.root);

        ent=new puppet("minecraft", 1,1);
        ent.debug=false; ent.color[1]=0.0f;ent.color[2]=0.0f;
        ent.setParent(scene.root);

        followCam.setLocation(0,5,-5.0f);
        followCam.lookAt(new float[] {0,2.0f,1.0f},new float[]{0,1.0f,0});
        followCam.setParent(ent);

        entity c2=new entity("Plane");
        c2.color[0]=0;c2.color[2]=0;
        c2.addLocation(0,-1,0);
        c2.setParent(scene.root);

        luz=new entity("piramid");
        luz.color[0]=0;c2.color[2]=0;
        luz.addLocation(0,5,0);
        luz.setParent(scene.root);

        rf=new rigidFloor(c2);
        scene.attachToPhisics(rf);

        cubeEnt=new rigidBody("piramid", 1,0);
        cubeEnt.debug=false; cubeEnt.color[0]=0.0f;cubeEnt.color[1]=0.0f;
        cubeEnt.setParent(scene.root);
        cubeEnt.setLocation(1,5.0f,1.0f);

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

        float a=(System.currentTimeMillis()-ultimo)/300.0f;
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
                vAng = ent.goToAngle(sceneManager.ejeZ, Tx, Ty, scene.getViewPort());
            Log.d("MyApp", "Angulo2: "+vAng+":"+Tx+":"+Ty);
        }

        long t2=System.currentTimeMillis();

        ent.makeItAlive(vMover, vAng, tm.getField(1).getClick(), deltaT);

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
        ambiente.getInstance().displayh=height;
        ambiente.getInstance().displayw=width;
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