package com.example.admin.opengles1;

import android.app.Activity;
import android.content.Context;
import android.opengl.GLSurfaceView;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.RadioGroup;

import com.example.admin.opengles1.*;

public class MainActivity extends AppCompatActivity {
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_main);
//    }
    //private GLSurfaceView mGLView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Create a GLSurfaceView instance and set it
        // as the ContentView for this Activity.
        //MyGLSurfaceView mGLView;
        //setContentView(mGLView);

        //mGLView =(MyGLSurfaceView) this.findViewById(R.id.viewport);
        setContentView(R.layout.activity_main);
    }
}

class MyGLSurfaceView extends GLSurfaceView {

    private final MyGLRenderer mRenderer;
    RadioGroup rg=null;
    Activity activity;
    public MyGLSurfaceView(Context context, AttributeSet attrs){
        super(context, attrs);

        // Create an OpenGL ES 2.0 context
        setEGLContextClientVersion(2);

        activity = (Activity) context;
        mRenderer = new MyGLRenderer(context);

        // Set the Renderer for drawing on the GLSurfaceView
        setRenderer(mRenderer);

        // Render the view only when there is a change in the drawing data
        //setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
   }

    private final float TOUCH_SCALE_FACTOR = 180.0f / 320;
    private float mPreviousX;
    private float mPreviousY;

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        mRenderer.onTouchEvent(e);

        // MotionEvent reports input details from the touch screen
        // and other input controls. In this case, you are only
        // interested in events where the touch position changed.

        float x = e.getX();
        float y = e.getY();

        if (rg == null) {
            rg = activity.findViewById(R.id.RGModo);
        }
/*
        if (rg.getCheckedRadioButtonId() == R.id.RBAccion && e.getAction()==MotionEvent.ACTION_UP && false)
        {
            if (x < getWidth() / 2 && y < getHeight() / 2)
                mRenderer.arriba();
            if (x > getWidth() / 2 && y > getHeight() / 2)
                mRenderer.abajo();
            if (x < getWidth() / 2 && y > getHeight() / 2)
                mRenderer.izquierda();
            if (x > getWidth() / 2 && y < getHeight() / 2)
                mRenderer.derecha();
        }
*/
        switch (e.getAction()) {
            case MotionEvent.ACTION_MOVE: {
                float dx = x - mPreviousX;
                float dy = y - mPreviousY;

                //mRenderer.setAngle( mRenderer.getAngle() + ((dx + dy) * TOUCH_SCALE_FACTOR));
                //int mode = rg.getCheckedRadioButtonId();

                if (rg.getCheckedRadioButtonId() == R.id.RBVolar)
                    mRenderer.volar(dx * TOUCH_SCALE_FACTOR, dy * TOUCH_SCALE_FACTOR);
                if (rg.getCheckedRadioButtonId() == R.id.RBCaminar)
                    mRenderer.caminar(dx * TOUCH_SCALE_FACTOR, dy * TOUCH_SCALE_FACTOR);
                if (rg.getCheckedRadioButtonId() == R.id.RBRotar)
                    mRenderer.rotar(dx * TOUCH_SCALE_FACTOR, dy * TOUCH_SCALE_FACTOR);
                if (rg.getCheckedRadioButtonId() == R.id.RBGirar)
                    mRenderer.girar(dx * TOUCH_SCALE_FACTOR);
                //requestRender();
                //Log.d("MyApp", "move: ");
                break;
            }
        }
        mPreviousX = x;
        mPreviousY = y;

        return true;
    }
}