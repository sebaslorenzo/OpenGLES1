package com.example.admin.opengles1;

import android.util.Log;

public class robot extends rigidBody {
    private state estado=state.inicial;
    private float timer;
    private boolean timerOn=false;

    enum state {inicial, parado, recto, doblad, doblai, salta, vuela, cae};

    private boolean enElPiso()
    {
        return estado!=state.salta && estado!=state.vuela && estado!=state.cae;
    }
    private boolean caminando() {return estado==state.recto || estado==state.doblad || estado==state.doblai;}

    public robot(String meshName, float vRadius, float shift)
    {
        super(meshName, vRadius, shift);
    }

    // ahora es seguro hacer la destruccion?
    public void destroy()
    {
        super.destroy();
    }

    public void makeItAlive(boolean mover, float angulo, boolean saltar, float dt)
    {
        timer-=dt;
        Log.d("MyApp", "Estado: "+estado+":"+ mover+":"+saltar);

        // esta saltando y toco el piso
        //if( estado==state.salta && collision )
        //    estado=state.inicial;

        // termino de caer
        if( estado==state.cae && isAminDone() )
            estado=state.inicial;

        // volando y toco el piso
        if( estado==state.vuela && collision )
        {
            estado=state.cae;   // cae
            playAction("Caer", mode.oneTime,0.5f,0);
        }
        // caminando y quedo en el aire. Arranco la accion Saltar por el medio
        else if( enElPiso() && !collision )
        {
            estado=state.vuela;
            playAction("Saltar", mode.oneTime,0.5f,0.41f);
        }
        // en el piso y salto
        else if( enElPiso() && saltar )
        {
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
            playAction("parado", mode.continous,0.5f,0);
        }
        else if( estado==state.salta && isAminDone() )   // termino de saltar, ahora vuela
        {
            estado=state.vuela;
        }

        if(mover && !timerOn && estado!=state.cae) {
            float speedR = estado==state.doblai||estado==state.doblad?2:1*(float)Math.PI/2;
            float speedT = 2.5f;
            float realAng=speedR * dt > Math.abs(angulo) ? angulo : speedR * dt * Math.signum(angulo);

            // La camara mira al -z, entonces le aplico la rotacion a ejeZn
            float[] direccion = getDirectionZ();
            addRotation(sceneManager.ejeY, realAng);
            move(direccion[0] * dt * speedT, 0, direccion[2] * dt * speedT);
        }
        if(timerOn && timer<=0)
        {
            timerOn=false;
            push(0,5,0);
        }
        Log.d("MyApp", "Estado: "+getAminDesc());
    }
}
