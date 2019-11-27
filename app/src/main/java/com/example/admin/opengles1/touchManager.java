package com.example.admin.opengles1;

import android.util.Log;
import android.view.MotionEvent;

import java.util.ArrayList;
import java.util.HashMap;

class touchField {
    boolean pressed;    // Si esta apretado o no
    float[] initial;    // Posición inicial al apoyar
    long downtime;      // Registra el inicio del touch
    long clickts;       // Registra el ultimo click
    float[] position;   // Posición inicial
    Boolean click;      // hubo click
    Boolean dclick;     // hubo dobleclick
    int x1;
    int x2;
    int y1;
    int y2;
    int touchId;
    int fieldId;

    public touchField(int id, int _x1, int _x2, int _y1, int _y2)
    {
        pressed=false;
        initial=new float[2];
        downtime=0;
        clickts=0;
        position=new float[2];  // Posición inicial
        click=false;            // hubo click
        dclick=false;           // hubo dobleclick
        x1=_x1;x2=_x2;
        y1=_y1;y2=_y2;
        fieldId=id;
        touchId=-1;
    }

    public boolean evalUbi(float x, float y)
    {
        int dw=ambiente.getInstance().displayw;
        int dh=ambiente.getInstance().displayh;
        return x>dw*x1/100.0f && x<dw*x2/100.0f && y>dh*y1/100.0f && y<dh*y2/100.0f;
    }

    public float deltaX()
    {
        return pressed?(position[0]-initial[0]):0;
    }
    public float deltaY()
    {
        return pressed?(position[1]-initial[1]):0;
    }
    public void resetInitialX() {initial[0]=position[0];}
    public void resetInitialY() {initial[1]=position[1];}

    public float deltaAng()
    {
        if(pressed)
            return (float)(Math.atan2(position[0]-initial[0],position[1]-initial[1]));
        return 0;
    }

    public float deltaX(float ratio)
    {
        if(pressed)
            return (position[0]-initial[0])/ratio;

        return 0;
    }

    public float deltaY(float ratio)
    {
        if(pressed)
            return (position[1]-initial[1])/ratio;

        return 0;
    }

    public boolean getClick()
    {
        boolean rc=click;
        click=false;
        return rc;
    }

    public boolean getDClick()
    {
        boolean rc=dclick;
        dclick=false;
        return rc;
    }
}

public class touchManager {
    private static touchManager instance;
    private touchManager()
    {
    }

    public static touchManager getInstance(){ return instance; }

    //static block initialization for exception handling
    static{
        try{
            instance = new touchManager();
        }catch(Exception e){
            throw new RuntimeException("Exception occured in creating singleton instance");
        }
    }

    ArrayList<touchField> fields=new ArrayList<touchField>();

    public touchField getField(int id)
    {
        for(touchField tf:fields)
            if (tf.fieldId==id)
                return tf;
        return null;
    }

    public void addField(int id, int _x1, int _y1, int _x2, int _y2)
    {
        fields.add(new touchField(id, _x1, _y1, _x2, _y2));
    }

    public void removeFields()
    {
        for(int i=0; i<fields.size(); i++)
           fields.remove(i);
    }

    public void removeField(int id)
    {
        for(int i=0; i<fields.size(); i++)
            if(fields.get(i).fieldId==id)
                fields.remove(i);
    }

    void dump()
    {
        Log.d("MyApp", "Touch: fields");
        for(int i=0; i<fields.size(); i++)
        {
            touchField tf=fields.get(i);
            Log.d("MyApp", "Touch: ("+i+")["+tf.touchId+"]<"+tf.click+">");
        }
    }

    public void onTouchEvent(MotionEvent e)
    {
        int index = e.getActionIndex();
        int pointer=e.getPointerId(index);

        Log.d("MyApp", "Touch: "+e.getActionMasked()+":"+ index+":"+pointer);

        //5
        if (e.getActionMasked()==MotionEvent.ACTION_DOWN || e.getActionMasked()==MotionEvent.ACTION_POINTER_DOWN)
        {
            for(touchField tf:fields)
            {
                if(tf.evalUbi(e.getX(index),e.getY(index)))
                {
                    tf.pressed=true;
                    if(tf.touchId==-1) {
                        tf.downtime = System.currentTimeMillis();
                        tf.initial[0] = tf.position[0] = e.getX(index);
                        tf.initial[1] = tf.position[1] = e.getY(index);
                        tf.touchId = pointer;
                    }
                }
            }
        }   //6
        else if (e.getActionMasked()==MotionEvent.ACTION_UP || e.getActionMasked()==MotionEvent.ACTION_POINTER_UP)
        {
            for(touchField tf:fields) {
                if (tf.touchId==pointer) {
                    tf.pressed = false;
                    if (System.currentTimeMillis() - tf.downtime < 300)
                    {
                        tf.click = true;
                        Log.d("MyApp", "Touch: delta: "+(System.currentTimeMillis() - tf.clickts));
                        if (System.currentTimeMillis() - tf.clickts < 500) {
                            tf.dclick = true;
                            tf.clickts = -1;   // evito que un tercer click de doble click
                        }
                        else
                            tf.clickts = System.currentTimeMillis();
                    }
                    tf.touchId=-1;
                    Log.d("MyApp", "Touch: delta");
                }
            }
        }
        else if (e.getActionMasked() == MotionEvent.ACTION_MOVE)    //2
        {
            for(touchField tf:fields) {
                if (tf.touchId==pointer) {
                    tf.position[0] = e.getX(index);
                    tf.position[1] = e.getY(index);
                }
            }
        }
        Log.d("MyApp", "Touch: end");
        //dump();
    }
}