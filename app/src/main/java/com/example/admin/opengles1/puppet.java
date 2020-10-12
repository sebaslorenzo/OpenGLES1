package com.example.admin.opengles1;

import android.opengl.Matrix;
import android.util.Log;

public class puppet extends rigidBody {
    private state estado=state.inicial;
    private float timer;
    private boolean timerOn=false;
    float lastCollision=0;
    public boolean lookAtMe=false;
    private float beeptimer=5.0f;

    enum state {inicial, parado, recto, doblad, doblai, salta, vuela, cae};

    private boolean enElPiso()
    {
        return estado!=state.salta && estado!=state.vuela && estado!=state.cae;
    }
    private boolean caminando() {return estado==state.recto || estado==state.doblad || estado==state.doblai;}

    public puppet(String meshName)
    {
        super(meshName);
    }

    // ahora es seguro hacer la destruccion?
    public void destroy()
    {
        super.destroy();
    }

    public void makeItAlive(boolean mover, float angulo, boolean saltar, float dt)
    {
        timer-=dt;
        lastCollision=collision?0.0f:lastCollision+dt;
        Log.d("MyApp", "Estado puppet: "+estado+":"+ mover+":"+saltar);

        // esta saltando y toco el piso
        //if( estado==state.salta && collision )
        //    estado=state.inicial;

        // termino de caer
        if( estado==state.cae && isAminDone() )
            estado=state.inicial;

        // volando y toco el piso (salto grande)
        if( estado==state.vuela && collision && isAminDone() )
        {
            estado=state.cae;   // cae
            playAction("Caer", mode.oneTime,0.5f,0);
        }
        // volando y toco el piso (salto chico)
        else if( estado==state.vuela && collision )
        {
            estado=state.inicial;   // cae
            //playAction("Caer", mode.oneTime,0.5f,0);
        }
        // caminando y quedo en el aire ma de 1/3s. Arranco la accion Saltar por el medio
        else if( enElPiso() && lastCollision>0.5f )
        {
            estado=state.vuela;
            playAction("Saltar", mode.oneTime,0.5f,0.41f);
        }
        // en el piso y salto
        else if( enElPiso() && saltar )
        {
            float[] ubi=getWorldLocation();
            Log.d("MyApp", "MiLugar: "+ubi[0]+";"+ubi[1]+";"+ubi[2]);
            estado=state.salta;
            playAction("Saltar", mode.oneTime,0.5f,0);
            timer=0.4f;
            timerOn=true;
        }
        // a moviendo recto
        else if( enElPiso() && estado!=state.recto && mover && Math.abs(angulo)<0.7 )   // pi/4
        {
            playAction("Caminar", mode.continous,0.5f,caminando()?getAminTime():0);
            estado=state.recto;
        }
        // a doblar Der
        else if( enElPiso() && estado!=state.doblad && mover && angulo<-0.7 )   // -pi/4
        {
            playAction("CaminarDer", mode.continous,0.5f,caminando()?getAminTime():0);
            estado=state.doblad;
        }
        // a doblar Izq
        else if( enElPiso() && estado!=state.doblai && mover && angulo>0.7 )       // pi/4
        {
            playAction("CaminarIzq", mode.continous,0.5f,caminando()?getAminTime():0);
            estado=state.doblai;
        }
        // moviendome y paro de golpe
        else if( enElPiso() && estado!=state.parado && !mover )
        {
            estado=state.parado;
            playAction(getIdleAnimName(), mode.oneTime,0.5f,0);
        }
        else if( estado==state.salta && isAminDone() )   // termino de saltar, ahora vuela
        {
            estado=state.vuela;
        }
        else if( estado==state.parado && isAminDone() )
        {
            playAction(getIdleAnimName(), mode.oneTime,0.5f,0);
        }

        if(mover && !timerOn && estado!=state.cae) {
            float speedR = estado==state.doblai||estado==state.doblad?2:1*(float)Math.PI/2;
            float speedT = 2.5f;
            float realAng=speedR * dt > Math.abs(angulo) ? angulo : speedR * dt * Math.signum(angulo);

            if(estado==state.doblai || estado==state.doblad )
                speedT=0;

            // La camara mira al -z, entonces le aplico la rotacion a ejeZn
            float[] direccion = getDirectionZ();
            addRotation(sceneManager.ejeY, realAng);
            move(direccion[0] * dt * speedT, 0, direccion[2] * dt * speedT);
        }

        if( estado==state.parado )
        {
            float realAng=(float)Math.PI * dt > Math.abs(angulo) ? angulo : (float)Math.PI * dt * Math.signum(angulo);
            addRotation(sceneManager.ejeY, realAng);
        }

        if(timerOn && timer<=0)
        {
            timerOn=false;
            push(0,5,0);    // salto hacia arriba
        }
        Log.d("MyApp", "Estado: "+getAminDesc());
    }

    public String getIdleAnimName()
    {
        return "parado";
    }

    public void testShot(float dt)
    {
        beeptimer-=dt;
        if(beeptimer<=0)
        {
            beeptimer=2.0f;
            for(int i=0; i<sceneManager.activeSceneManager.scenePhoto.size(); i++)
            {
                entityPhoto ef=sceneManager.activeSceneManager.scenePhoto.get(i);
                if(ef.entityCopy.id==1)
                {
                    float[] o=sceneManager.activeSceneManager.viewPort.getWorldLocation();
                    float[] u=sceneManager.activeSceneManager.viewPort.getDirectionZn();

                    if(sceneManager.activeSceneManager.phy.testBodyRay(sceneManager.activeSceneManager.scenePhoto,i,o,u))
                    {
                        soundManager.getInstance().playSound("beep.wav", 0.5f, 0.5f);
                        Log.d("MyApp", "Hit:"+beeptimer);
                    }
                }
            }
        }
    }
}
