package com.example.admin.opengles1;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.SoundPool;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Build;
import android.util.Log;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import static android.opengl.GLES20.glUniform4fv;
import static java.lang.Math.PI;

// Cualquier cosa que tiene que ser movida
class movable
{
    private float[] matrix=new float[16];

    public void setIdentity() { Matrix.setIdentityM(matrix,0); }
    public float[] getMatrixClone()
    {
        return matrix.clone();
    }

    public void setLocation( float x, float y, float z)
    {
        matrix[12]=x;
        matrix[13]=y;
        matrix[14]=z;
    }

    public void setLocation( float[] dest) {setLocation(dest[0], dest[1], dest[2]);}

    public float[] getLocation()
    {
        float[] rv=new float[3];
        rv[0]=matrix[12];
        rv[1]=matrix[13];
        rv[2]=matrix[14];
        return rv;
    }

    public void addLocation( float x, float y, float z)
    {
        matrix[12]+=x;
        matrix[13]+=y;
        matrix[14]+=z;
    }

    public void setRotation( float[] v, float a)
    {
        float[] loc = getLocation();
        Matrix.setRotateM(matrix, 0, a * 180 / (float) PI, v[0], v[1], v[2]);
        setLocation(loc);
    }

    public void addRotation( float[] v, float a)
    {
        float[] rot=new float[16];
        float[] ori=getMatrixClone();
        Matrix.setRotateM(rot, 0, a * 180.0f / (float) Math.PI, v[0], v[1], v[2]);
        Matrix.multiplyMM(matrix, 0, ori, 0, rot, 0);
    }

    public void lookAt(float[] look, float[] up)
    {
        float[] ojos = getLocation();
        Matrix.setLookAtM(matrix,0,ojos[0],ojos[1],ojos[2],look[0],look[1],look[2],up[0],up[1],up[2]);
        setLocation(ojos);
    }

    public void pitch(float angle)
    {
        addRotation(new float[]{1.0f,0,0}, angle);
    }

    public void yaw(float angle)
    {
        addRotation(new float[]{0,1.0f,0}, angle);
    }

    public void roll(float angle)
    {
        addRotation(new float[]{0,0,1.0f}, angle);
    }

    public boolean isParent()   {return false;}
    public boolean isDrawable()   {return false;}
    public boolean isDinamic()   {return false;}
}

// es parte de una escena
class sceneable extends movable
{
    boolean active=true;
    public node parent=null;

    public void setParent(node p)
    {
        if(parent!=null)
            removeParent();

        parent=p;
        parent._makeChild(this);
    }

    public void removeParent()
    {
        if(parent!=null)
        {
            parent._removeChild(this);
            parent=null;
        }
    }

    public float[] getWorldMatrix()
    {
        float[] scratch=getMatrixClone();
        float[] scratch2=new float[16];
        node n=parent;
        while(n!=null)
        {
            Matrix.multiplyMM(scratch2, 0, n.getMatrixClone(), 0, scratch, 0);
            n=n.parent;
            System.arraycopy(scratch2,0,scratch,0,16);
        }
        return scratch;
    }

    public void enable() {active=true;}
    public void disable() {active=false;}
    public boolean isEnabled() {return active;}

    public float[] getDirectionX() {
        float[] td = {1.0f, 0, 0, 1.0f}; return getDirection(td);}

    public float[] getDirectionXn() {
        float[] td = {-1.0f, 0, 0, 1.0f}; return getDirection(td);}

    public float[] getDirectionY() {
        float[] td = {0, 1.0f, 0, 1.0f}; return getDirection(td);}

    public float[] getDirectionYn() {
        float[] td = {0, -1.0f, 0, 1.0f}; return getDirection(td);}

    public float[] getDirectionZ() {
        float[] td = {0, 0, 1.0f, 1.0f}; return getDirection(td);}

    public float[] getDirectionZn() {
        float[] td = {0, 0, -1.0f, 1.0f}; return getDirection(td);}

    private float[] getDirection(float[] d)   // direccion real
    {
        float[] dir = new float[4];
        float[] wm = getWorldMatrix();
        Matrix.multiplyMV(dir, 0, getWorldMatrix(),0,d,0);
        dir[0]-=wm[12]; // elimino la parte de traslacion
        dir[1]-=wm[13];
        dir[2]-=wm[14];
        Log.d("worldmatrix", dir[0] +" "+ dir[2] +" "+ dir[2]);
        return dir;
    }

    // calcula la rotacion hacia la direccion Target (base camara) que es tomada del touch.
    // Eje es la cara que representa el frente del objeto, SpeedR es la velocidad de Rotacion
    // La camara mira a -z

    public float goToAngle(float [] eje, float targetX, float targetY, camera cam)
    {
        // Target son las coordenadas de la pantalla
        double Tl=Math.sqrt(targetX*targetX+targetY*targetY);
        float Tx=(float)(targetX/Tl);
        float Ty=(float)(-targetY/Tl);

        // La camara mira al -z, entonces le aplico la rotacion a ejeZn
        float[] direccion = cam.getDirectionZn();
        float cx=direccion[0];
        float cz=direccion[2];

        // El objeto a animar mira segun el parametro eje
        direccion = getDirection(eje);

        float ox=direccion[0];
        float oz=direccion[2];

        // formula de aplicar la matriz de rotacion de Cam a Obj al vector (1,0,0) eje x que defini como arriba
        float Wx=cx*oz-ox*cz;
        float Wy=cx*ox+cz*oz;

        double Wl=Math.sqrt(Wx*Wx+Wy*Wy);
        Wx=(float)(Wx/Wl);
        Wy=(float)(Wy/Wl);
        float Px=Wy;
        float Py=-Wx;

        float prod=Math.min(Math.max(Tx*Wx+Ty*Wy,-1.0f),1.0f);
        return -(float)Math.abs(Math.acos(prod))*Math.signum(Tx*Px+Ty*Py);
    }
    // orienta la entidad hacia la direccion Target que es tomada del touch. (direccion vista desde arriba)
    // Eje es la cara que representa el frente del objeto, SpeedR/T la velocidad de Rotacion y Translacion
    // deberia tener en cuenta a donde mira la camara no?
    // La camara mira a -z

    public void goTo(float [] eje, float speedR, float speedT, float dt, float targetX, float targetY, camera cam )
    {
        float[] ejeY = new float[3];
        ejeY[0]=ejeY[2]=0; ejeY[1]=1.0f;

        float realAngle=(float)goToAngle(eje, targetX, targetY, cam);
        double angle=speedR*dt;

        // La camara mira al -z, entonces le aplico la rotacion a ejeZn
        addRotation(ejeY,speedR*dt>Math.abs(realAngle)?realAngle:speedR*dt*Math.signum(realAngle));
        float[] direccion = getDirection(eje);
        addLocation(direccion[0]*dt*speedT,0,direccion[2]*dt*speedT);
    }
}

class node extends sceneable
{
    public ArrayList<sceneable> children;

    public node()
    {
        parent=null;
        children=new ArrayList<sceneable>();
        setIdentity();
    }

    public void _makeChild(sceneable s) {children.add(s);}
    public void _removeChild(sceneable s) {children.remove(s);}
    public boolean isParent()
    {
        return children.size()>0;
    }
}

public class sceneManager {
    public node root;
    private camera viewPort;
    public long time;
    private float deltatime;
    float actionTime;
    private int mProgram;
    private float[] lightSource;
    public physics phy=null;
    public static final float[] ejeX = {1, 0, 0, 1};
    public static final float[] ejeY = {0, 1, 0, 1};
    public static final float[] ejeZ = {0, 0, 1, 1};
    public static final float[] ejeXn = {-1, 0, 0, 1};
    public static final float[] ejeYn = {0, -1, 0, 1};
    public static final float[] ejeZn = {0, 0, -1, 1};
    public float[] mProjectionMatrix = new float[16];

    private final String vertexShaderCode =
    "uniform vec4 uColor;" +    // color de base del objeto
    "uniform vec4 vLightSource;" +    // Posicion global de la luz
    "uniform mat4 uMVPMatrix;"+ // matriz de transformacion
    "uniform mat4 uObjMatrix;"+ // matriz del objeto
    "uniform mat4 uBone[100];"+ // posicion de cada hueso
    "attribute vec4 vPosition;"+// vertex
    "attribute vec4 vNormal;"+  // normal
    "attribute vec2 vIndex;"+   // Que hueso influye
    "attribute vec2 vWeight;"+  // peso de cada hueso
    "attribute vec2 vCoord;"+   // textura
    "varying float light;"+     // luz calculada
    "varying vec2 vCoord2;"+    // textura para el 2do shader
    "uniform lowp int uTextured;"+  // Usa textura?
    "uniform lowp int uRigged;"+    // usa armadura?
    "void main() {"+
    "    vec4 normalShift = vec4(vec3(vPosition)+vec3(vNormal),1.0);"+  // punta de la normal en el espacio
    "    vec4 newVertex;"+
    "    vec4 newNormal;"+
    "    int index;"+
    "    if(uRigged==1 && vWeight.x > 0.0) {"+
    "        index=int(vIndex.x);"+
    "        newVertex = (uBone[index] * vPosition) * vWeight.x;"+
    "        newNormal = (uBone[index] * normalShift)   * vWeight.x;"+
    "        index=int(vIndex.y);"+
    "        newVertex = (uBone[index] * vPosition) * vWeight.y + newVertex;"+
    "        newNormal = (uBone[index] * normalShift)   * vWeight.y + newNormal;"+
    "    } else {"+
    "        newVertex = vPosition;"+
    "        newNormal = normalShift;"+
    "    }"+
    "    gl_Position = uMVPMatrix * newVertex;"+
    //"    float LdotN = clamp(dot(normalize(vec3(vLightSource)),vec3(vNormal)),0.2,1.0);"+
    //"    light = LdotN;"+
    "    vec3 fragPos = vec3(uObjMatrix * newVertex);"+
    "    vec3 normVal = vec3(uObjMatrix * newNormal) - fragPos;"+
    "    vec3 lightDir = normalize(vec3(vLightSource) - fragPos);"+
    "    light=clamp(dot(normVal, lightDir),0.0,1.0);"+
    "    if( uTextured==1 ) {"+
    "        vCoord2 = vCoord;"+
    "    }"+
    "}";

    private final String fragmentShaderCode =
    "precision mediump float;" +
    "uniform vec4 uColor;" +
    "uniform vec4 vLightSource;" +    // Posicion global de la luz
    "uniform mat4 uMVPMatrix;"+ // matriz de transformacion
    "uniform sampler2D vTex;" +
    "varying vec2 vCoord2;" +
    "varying float light;" +
    "uniform lowp int uTextured;" +
    "uniform lowp int uRigged;" +
    "void main() {" +
    "  float color=clamp(light+0.2,0.0,1.0);"+
    "  if(uTextured==1)"+
    "    gl_FragColor = color*texture2D(vTex, vCoord2);"+
    "  else"+
    "    gl_FragColor = vec4(vec3(uColor)*color,1.0);"+
    //"  gl_FragColor = vec4(vec3(color),1.0);"+
    "}";

    void draw()
    {
        ArrayList<entity> objetos=new ArrayList<>();
        ArrayList<float[]> gpos=new ArrayList<>();

        // lista los objetos y recalcula cada accion
        listNodes(root,root.getMatrixClone(), objetos, gpos);

        viewPort.calcViewMatrix(mProjectionMatrix);
        // general settings
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LESS);
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glCullFace(GLES20.GL_BACK);
        GLES20.glFrontFace(GLES20.GL_CCW);
        // Fresh start, clear buffers
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        for(int i=0; i<objetos.size(); i++)
            if(objetos.get(i).isVisible())
                drawMesh(objetos.get(i), gpos.get(i));
    }

    public float deltaTime()
    {
        return deltatime;
    }

    public float updateDeltaTime()
    {
        long now=System.currentTimeMillis();
        //long now= SystemClock.uptimeMillis();
        deltatime=(now-time)/1000.0f;
        time=now;
        Log.d("MyApp", "Delta: " + now +" "+ deltatime);
        return deltatime;  //return dt>0.1?0.1f:dt;
    }

    public void resetTime()
    {
        time=System.currentTimeMillis();
    }

    void listNodes(node n, float[] pRotPos, ArrayList<entity> objetos, ArrayList<float[]> matrices) {
        float[] scratch = new float[16];

        for (sceneable c : n.children) {
            if( c.isEnabled()) {
                if (c.isDrawable()) {
                    Matrix.multiplyMM(scratch, 0, pRotPos, 0, c.getMatrixClone(), 0);
                    ((entity) c).addTime(deltatime);
                    objetos.add(((entity) c));
                    matrices.add(scratch.clone());
                }
                if (c.isParent()) {
                    Matrix.multiplyMM(scratch, 0, pRotPos, 0, c.getMatrixClone(), 0);
                    listNodes((node) c, scratch, objetos, matrices);
                }
            }
        }
    }

    void drawNode(node n, float[] pRotPos, float dt) {
        float[] scratch = new float[16];

        for (sceneable c : n.children) {
            if( c.isEnabled()) {
                if (c.isDrawable()) {
                    Matrix.multiplyMM(scratch, 0, pRotPos, 0, c.getMatrixClone(), 0);
                    ((entity) c).addTime(dt);   // suma tiempos y calcula las accions
                    // real opengl draw
                    drawMesh((entity) c, scratch);
                }
                if (c.isParent()) {
                    Matrix.multiplyMM(scratch, 0, pRotPos, 0, c.getMatrixClone(), 0);
                    drawNode((node) c, scratch, dt);
                }
            }
        }
    }

    void enablePhisics() {if(phy==null) phy=new physics();}
    void disablePhisics() {phy=null;}
    void attachToPhisics(rigidFloor f) {if(phy!=null) phy.pisos.add(f);}
    void setLightSource(float x, float y, float z)
    {
        lightSource[0]=x;lightSource[1]=y;lightSource[2]=z;lightSource[3]=1;
    }

    public sceneManager(camera cam)
    {
        viewPort=cam;
        root=new node();
        time=System.currentTimeMillis();
        deltatime=0;
        actionTime=0;
        lightSource=new float[4];

        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);

        // create empty OpenGL ES Program
        mProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(mProgram, vertexShader);
        GLES20.glAttachShader(mProgram, fragmentShader);
        GLES20.glLinkProgram(mProgram);

        if(GLES20.glGetError()!=GLES20.GL_NO_ERROR)
            Log.d("MyApp", "Error linking shaders");
    }

    public camera getViewPort() {return viewPort;}
    public void setViewPort(camera cam) {viewPort=cam;}

    public int loadShader(int type, String shaderCode){
        int shader = GLES20.glCreateShader(type);

        // add the source code to the shader and compile it
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);

        // Get the compilation status.
        final int[] compileStatus = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);

        // If the compilation failed, delete the shader.
        if (compileStatus[0] == 0) {
            Log.d("MyApp", "Error compiling shader: " + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            shader = 0;
        }

        return shader;
    }

    // number of coordinates per vertex in this array
    static final int COORDS_PER_VERTEX = 3;
    static final int VERTEX_PER_FACE = 3;

    void checkGlError()
    {
        int err=GLES20.glGetError();
        boolean ind=GLES20.glIsProgram(mProgram);
        String str1=GLES20.glGetProgramInfoLog(mProgram);

        if(err!=GLES20.GL_NO_ERROR) {
            Log.d("MyApp", err + ":" + GLES20.glGetString(err));
        }
    }

    public void drawMesh(entity e, float[] posRot) {
        float[] mMVPMatrix = new float[16];
        int vertexStride = COORDS_PER_VERTEX * 4; // 4 bytes per vertex
        int indexStride = 2 * 4; // 4 bytes per vertex
        boolean isRigged;
        boolean isTextured;

        meshReader m = e.mesh;
        // Add program to OpenGL ES environment
        GLES20.glUseProgram(mProgram);

        isRigged = m.skeleton;
        isTextured = !e.debug && m.textured;

        // get handle to fragment shader's uColor member
        int mColorHandle = GLES20.glGetUniformLocation(mProgram, "uColor");
        assert mColorHandle >= 0 : "Error al cargar uColor";
        glUniform4fv(mColorHandle, 1, e.color, 0);

        // get handle to light source
        int mLightSource = GLES20.glGetUniformLocation(mProgram, "vLightSource");
        assert mLightSource >= 0 : "Error al cargar mLightSource";
        glUniform4fv(mLightSource, 1, lightSource, 0);

        // get handle to Object Matrix
        int mObjMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uObjMatrix");
        assert mObjMatrixHandle >= 0 : "Error al cargar mObjMatrixHandle";
        GLES20.glUniformMatrix4fv(mObjMatrixHandle, 1, false, posRot, 0);

        // get handle to uRigged flag
        int mRigged = GLES20.glGetUniformLocation(mProgram, "uRigged");
        assert mRigged >= 0 : "Error al cargar mRigged";
        GLES20.glUniform1i(mRigged, isRigged ? 1 : 0);

        // get handle to uTextured flag
        int mTextured = GLES20.glGetUniformLocation(mProgram, "uTextured");
        assert mTextured >= 0 : "Error al cargar mTextured";
        GLES20.glUniform1i(mTextured, isTextured ? 1 : 0);

        int mPositionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition");
        assert mPositionHandle >= 0 : "Error al cargar mPositionHandle";
        GLES20.glEnableVertexAttribArray(mPositionHandle);

        if (!e.debug)
            GLES20.glVertexAttribPointer(mPositionHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, vertexStride, m.vertexBuffer);
        else
            GLES20.glVertexAttribPointer(mPositionHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, vertexStride, m._vertexBuffer);

        int mNormalHandle = GLES20.glGetAttribLocation(mProgram, "vNormal");
        assert mNormalHandle >= 0 : "Error al cargar mNormalHandle";
        GLES20.glEnableVertexAttribArray(mNormalHandle);

        if (!e.debug)
            GLES20.glVertexAttribPointer(mNormalHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT, true, vertexStride, m.normalBuffer);
        else
            GLES20.glVertexAttribPointer(mNormalHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT, true, vertexStride, m._normalBuffer);

        int mMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
        assert mMVPMatrixHandle >= 0 : "Error al cargar mMVPMatrixHandle";
        Matrix.multiplyMM(mMVPMatrix, 0, viewPort.mVPMatrix, 0, posRot, 0);
        GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mMVPMatrix, 0);

        int mIndexHandle = GLES20.glGetAttribLocation(mProgram, "vIndex");
        assert mIndexHandle >= 0 : "Error al cargar mIndexHandle";
        GLES20.glEnableVertexAttribArray(mIndexHandle);

        int mWeightHandle = GLES20.glGetAttribLocation(mProgram, "vWeight");
        assert mWeightHandle >= 0 : "Error al cargar mWeightHandle";
        GLES20.glEnableVertexAttribArray(mWeightHandle);

        int mBoneHandle = GLES20.glGetUniformLocation(mProgram, "uBone");
        assert mBoneHandle >= 0 : "Error al cargar mBoneHandle";

        if (isRigged) {
            if (!e.debug) {
                GLES20.glVertexAttribPointer(mIndexHandle, 2, GLES20.GL_FLOAT, true, indexStride, m.boneBuffer);
                GLES20.glVertexAttribPointer(mWeightHandle, 2, GLES20.GL_FLOAT, true, indexStride, m.weightBuffer);
            } else {
                GLES20.glVertexAttribPointer(mIndexHandle, 2, GLES20.GL_FLOAT, true, indexStride, m._boneBuffer);
                GLES20.glVertexAttribPointer(mWeightHandle, 2, GLES20.GL_FLOAT, true, indexStride, m._weightBuffer);
            }
            long t0=System.currentTimeMillis();
            // ponder aca la maxima cantidad de huesos
            float[] matrices = new float[16 * m.huesos.size()];
            e.getAnimMatrix(matrices);
            Log.d("MyApp", "Time esqueleto: "+(System.currentTimeMillis()-t0));

            GLES20.glUniformMatrix4fv(mBoneHandle, m.huesos.size(), false, matrices, 0);
        } else {
            // los mando a alguna info cualquiera
            GLES20.glVertexAttribPointer(mIndexHandle, 2, GLES20.GL_FLOAT, true, indexStride, m.vertexBuffer);
            GLES20.glVertexAttribPointer(mWeightHandle, 2, GLES20.GL_FLOAT, true, indexStride, m.vertexBuffer);
            GLES20.glUniformMatrix4fv(mBoneHandle, 1, false, new float[16], 0);
        }

        int mUvCoords = GLES20.glGetAttribLocation(mProgram, "vCoord");
        assert mUvCoords >= 0 : "Error al cargar mUvCoords";
        GLES20.glEnableVertexAttribArray(mUvCoords);
// sebas
        int mTextureUniformHandle = GLES20.glGetUniformLocation(mProgram, "vTex");
        assert mTextureUniformHandle >= 0 : "Error al cargar mTextureUniformHandle";
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0+m.textureSlot); // Set the active texture unit to texture unit 0.
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, m.textureHandle[0]); // Bind the texture to this unit.
        GLES20.glUniform1i(mTextureUniformHandle, m.textureSlot);   // Tell the sampler to use this texture in unit 0.

        if (isTextured)
            GLES20.glVertexAttribPointer(mUvCoords, 2, GLES20.GL_FLOAT, true, indexStride, m.uvBuffer);
        else
            GLES20.glVertexAttribPointer(mUvCoords, 2, GLES20.GL_FLOAT, true, indexStride, m.vertexBuffer);

        if(!e.debug) {
            //GLES20.glDisable(GLES20.GL_CULL_FACE);
            // Draw the triangles
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, m.vertexCount);
        }
        else    //debug
        {
            // Draw the lines
            GLES20.glLineWidth(3.0f);
            GLES20.glDrawArrays(GLES20.GL_LINES, 0, m._vertexCount);
        }
        GLES20.glDisableVertexAttribArray(mPositionHandle);
    }
}

class soundManager
{
    private static soundManager instance;
    private soundManager(){}

    //static block initialization for exception handling
    static{
        try{
            instance = new soundManager();
            instance.init(5);
        }catch(Exception e){
            throw new RuntimeException("Exception occured in creating singleton instance");
        }
    }

    public static soundManager getInstance(){
        return instance;
    }

    SoundPool soundPool;
    HashMap<String,Integer> catalogo;

    private void init(int maxStreams)
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            soundPool = new SoundPool.Builder()
                    .setMaxStreams(maxStreams)
                    .build();
        } else {
            //soundPool = new SoundPool(maxStreams, AudioManager.STREAM_MUSIC, 0);
        }
        catalogo=new HashMap<String,Integer>();
    }

    int loadSound(String filename)
    {
        int id=getSoundId(filename);

        if(id==-1) {
            Log.d("MyApp","Loading sound "+filename);
            try {
                id = soundPool.load(ambiente.getInstance().getContext().getAssets().openFd(filename), 1);
                catalogo.put(filename, (Integer)id);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        return id;
    }

    int getSoundId(String filename)
    {
        if(catalogo.containsKey(filename))
            return (int)catalogo.get(filename);
        return -1;
    }

    // si no esta cargado lo carga y despues lo ejecuta
    void playSound(String filename, float r, float l) {
        int id=getSoundId(filename);
        if(id==-1)
            id=loadSound(filename);

        playSound(id, r, l);
    }

    void playSound(int sound, float r, float l) {
        soundPool.play(sound, l, r, 1, 0, 1);
    }
}
/*
class MediaPlayerMgr extends MediaPlayer
{
    boolean idle;
    private final ReentrantLock lock;

    public MediaPlayerMgr()
    {
        super();
        idle=true;
        lock= new ReentrantLock();
        setAudioStreamType(AudioManager.STREAM_MUSIC);

        setOnCompletionListener(    new MediaPlayer.OnCompletionListener() {
            public void onCompletion(MediaPlayer mpl) {
                mpl.reset();
                lock.lock();  // block until condition holds
                try {
                    idle=true;
                } finally {
                    lock.unlock();
                }
            }
        });

        setOnPreparedListener(  new MediaPlayer.OnPreparedListener() {
            public void onPrepared(MediaPlayer mpl) {
                mpl.start();
            }
        });
    }

    boolean play(String filename)
    {
        boolean playIt=false;
        lock.lock();  // block until condition holds
        try {
            if(idle)
            {
                idle=false;
                playIt=true;
            }
        } finally {
            lock.unlock();
        }

        if(playIt)
        {
            try {
                AssetFileDescriptor afd = ambiente.getInstance().getContext().getAssets().openFd(filename);
                setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                prepareAsync();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        return playIt;
    }
}
*/
class camera extends sceneable
{
    public float[] mVPMatrix = new float[16];          // world to screen view
    //public float[] mProjectionMatrix = new float[16];  // camera to screen view

    public camera(float[] vojos, float[] vadelante, float[] varriba)
    {
        setLocation(vojos[0], vojos[1],vojos[2]);
        lookAt(vadelante,varriba);
        //calcViewMatrix();
    }

    public camera()
    {
        setIdentity();
        //calcViewMatrix();
    }

    void calcViewMatrix(float[] ProjectionMatrix )
    {
        float[] scratch=new float[16];
        Matrix.invertM(scratch,0,getWorldMatrix(),0);
        Matrix.multiplyMM(mVPMatrix, 0, ProjectionMatrix, 0,scratch , 0);
    }
}

class ambiente {

    private static ambiente instance;

    private ambiente(){}

    //static block initialization for exception handling
    static{
        try{
            instance = new ambiente();
        }catch(Exception e){
            throw new RuntimeException("Exception occured in creating singleton instance");
        }
    }

    public static ambiente getInstance(){
        return instance;
    }

    public Context contextCopy;
    public void setContext(Context c) {contextCopy=c;}
    public Context getContext() {return contextCopy;}
    public int displayw;
    public int displayh;
}

enum mode {oneTime, continous, stopped, boomerang}

class actionState
{
    int actionId;
    mode looping;
    float blend;
    float time;
    float weight;
    animation anim; // copia de la animacion

    // maneja los estdos de una animacion de un mesh determinado
    public actionState(meshReader m, String name, mode loopMode, float blendTime, float startAt)
    {
        time=startAt;
        actionId=-1;
        looping=mode.stopped;
        anim=null;
        weight = 1;

        if( m.skeleton ) {
            for (int i = 0; i < m.animaciones.size(); i++)
            {
                if (m.animaciones.get(i).name.equals(name)) {
                    actionId = i;
                    anim = m.animaciones.get(i);
                    looping = loopMode;
                    blend = blendTime;
                }
            }
        }
    }

    public void addTime(float dt)
    {
        // si hhay que mezclar y queda todavia fuerza en la accion secundaria
        if(blend>0 && weight>0.0f)
            weight=Math.max(weight-dt/blend,0.0f);

        for(int i=0; i<anim.soundEvents.size();i++)
        {
            soundEvent se=anim.soundEvents.get(i);
            if(time<se.time && se.time<time+dt)
                soundManager.getInstance().playSound(se.filename, se.right, se.left);
        }
        switch(looping)
        {
            case oneTime:
            {
                time+=dt;
                if(time>anim.length)
                {
                    time=anim.length;
                    looping=mode.stopped;
                }
                break;
            }
            case continous:
            {
                time+=dt;
                while(time>anim.length)
                    time-=anim.length;
                break;
            }
            case boomerang:
            {
                time+=dt;
                while(time>anim.length)
                    time-=2*anim.length;
                break;
            }
        }
        Log.d("MyApp", "Reel+ "+ time);
    }
}

// manejador de texturas con un catalogo
class imageManager
{
    // Necesarias para el singleton
    private static imageManager instance;
    private imageManager()
    {
        catalogo=new HashMap<String,Integer>();
        bitmaps=new ArrayList<Bitmap>();

    }
    public static imageManager getInstance(){ return instance; }

    //static block initialization for exception handling
    static{
        try{
            instance = new imageManager();
        }catch(Exception e){
            throw new RuntimeException("Exception occured in creating singleton instance");
        }
    }

    HashMap<String,Integer> catalogo;
    ArrayList<Bitmap> bitmaps;

    public int size() {return catalogo.size();}

    public int loadImage(String filename)
    {
        if(catalogo.containsKey(filename))
            return (int)catalogo.get(filename);

        try
        {
            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;   // No pre-scaling

            InputStream is=ambiente.getInstance().getContext().getAssets().open(filename);
            int id=bitmaps.size();
            Bitmap bm=BitmapFactory.decodeStream(is);
            bitmaps.add(bm);
            catalogo.put(filename, id);
            return id;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    public Bitmap getImage(String filename)
    {
        return bitmaps.get(loadImage(filename));
    }

    public int getImageId(String filename)
    {
        return loadImage(filename);
    }

    public Bitmap getImage(int id)
    {
        return bitmaps.get(id);
    }
}
/*
si las texturas se cargan una sola vez:
    el mesh deberia tener los handlers
si se cargan cada vez
    el mesh deberia tener el bitmap
    los UV deberian borrarse y cargarse cada vez
 */