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
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static android.opengl.GLES20.glUniform4fv;
import static java.lang.Math.PI;

// Cualquier cosa que tiene que ser movida
class Movable
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
class Sceneable extends Movable
{
    boolean active=true;
    public Node parent=null;

    public void setParent(Node p)
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

    public float[] getWorldMatrixClone()
    {
        float[] scratch=getMatrixClone();
        float[] scratch2=new float[16];
        Node n=parent;
        while(n!=null)
        {
            Matrix.multiplyMM(scratch2, 0, n.getMatrixClone(), 0, scratch, 0);
            n=n.parent;
            System.arraycopy(scratch2,0,scratch,0,16);
        }
        return scratch;
    }

    public float[] getWorldLocationClone()
    {
        float[] scratch= getWorldMatrixClone();
        float[] scratch2=new float[4];
        scratch2[0]=scratch[12];
        scratch2[1]=scratch[13];
        scratch2[2]=scratch[14];
        scratch2[3]=1;
        return scratch2;
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
        float[] wm = getWorldMatrixClone();
        Matrix.multiplyMV(dir, 0, getWorldMatrixClone(),0,d,0);
        dir[0]-=wm[12]; // elimino la parte de traslacion
        dir[1]-=wm[13];
        dir[2]-=wm[14];
        Log.d("worldmatrix", dir[0] +" "+ dir[2] +" "+ dir[2]);
        return dir;
    }

    // calcula la rotacion hacia la direccion Target (base camara) que es tomada del touch.
    // Eje es la cara que representa el frente del objeto, SpeedR es la velocidad de Rotacion
    // La camara mira a -z

    public float goToAngle(float [] eje, float targetX, float targetY, Camera cam)
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

    public void goTo(float [] eje, float speedR
            , float speedT, float dt, float targetX, float targetY, Camera cam )
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

class Node extends Sceneable
{
    public ArrayList<Sceneable> children;

    public Node()
    {
        parent=null;
        children=new ArrayList<Sceneable>();
        setIdentity();
    }

    public void _makeChild(Sceneable s) {children.add(s);}
    public void _removeChild(Sceneable s) {children.remove(s);}
    public boolean isParent()
    {
        return children.size()>0;
    }
}

// guarda una copia de las entidades de la escena y las posiciones del objeto y huesos para usar en este ciclo
class EntityPhoto
{
    Entity entityCopy;
    float[] rotPosCopy;
    float[] skeletonCopy;
    float[] rea;    // reaccion del cuerpo rigido

    public EntityPhoto(Entity e, float[] pr, float[] s)
    {
        entityCopy=e;
        rotPosCopy=pr;
        skeletonCopy=s;
        rea=new float[4];
        rea[0]=rea[1]=rea[2]=0;rea[3]=1;
    }
}

public class SceneManager {
    public Node root;
    public Camera viewPort;
    public long time;
    private float deltatime;
    float actionTime;
    private int mProgram;
    private float[] lightSource;
    public Physics phy = null;
    public static final float[] ejeX = {1, 0, 0, 1};
    public static final float[] ejeY = {0, 1, 0, 1};
    public static final float[] ejeZ = {0, 0, 1, 1};
    public static final float[] ejeXn = {-1, 0, 0, 1};
    public static final float[] ejeYn = {0, -1, 0, 1};
    public static final float[] ejeZn = {0, 0, -1, 1};
    public float[] mProjectionMatrix = new float[16];
    public List<EntityPhoto> scenePhoto;
    public static SceneManager activeSceneManager;

    private final String vertexShaderCode =
            "uniform vec4 uColor;" +    // color de base del objeto
                    "uniform vec4 vLightSource;" +    // Posicion global de la luz
                    "uniform mat4 uMVPMatrix;" + // matriz de transformacion
                    "uniform mat4 uObjMatrix;" + // matriz del objeto
                    "uniform mat4 uBone[100];" + // posicion de cada hueso
                    "attribute vec4 vPosition;" +// vertex
                    "attribute vec4 vNormal;" +  // normal
                    "attribute vec2 vIndex;" +   // Que hueso influye
                    "attribute vec2 vWeight;" +  // peso de cada hueso
                    "attribute vec2 vCoord;" +   // textura
                    "varying float light;" +     // luz calculada
                    "varying vec2 vCoord2;" +    // textura para el 2do shader
                    "uniform lowp int uTextured;" +  // Usa textura?
                    "uniform lowp int uRigged;" +    // usa armadura?
                    "void main() {" +
                    "    vec4 normalShift = vec4(vec3(vPosition)+vec3(vNormal),1.0);" +  // punta de la normal en el espacio
                    "    vec4 newVertex;" +
                    "    vec4 newNormal;" +
                    "    int index;" +
                    "    if(uRigged==1 && vWeight.x > 0.0) {" +
                    "        index=int(vIndex.x);" +
                    "        newVertex = (uBone[index] * vPosition) * vWeight.x;" +
                    "        newNormal = (uBone[index] * normalShift)   * vWeight.x;" +
                    "        if(vWeight.y > 0.0) {" +
                    "            index=int(vIndex.y);" +
                    "            newVertex = (uBone[index] * vPosition) * vWeight.y + newVertex;" +
                    "            newNormal = (uBone[index] * normalShift)   * vWeight.y + newNormal;" +
                    "        }" +
                    "    } else {" +
                    "        newVertex = vPosition;" +
                    "        newNormal = normalShift;" +
                    "    }" +
                    "    gl_Position = uMVPMatrix * newVertex;" +
                    //"    float LdotN = clamp(dot(normalize(vec3(vLightSource)),vec3(vNormal)),0.2,1.0);"+
                    //"    light = LdotN;"+
                    "    vec3 fragPos = vec3(uObjMatrix * newVertex);" +
                    "    vec3 normVal = vec3(uObjMatrix * newNormal) - fragPos;" +
                    "    vec3 lightDir = normalize(vec3(vLightSource) - fragPos);" +
                    "    light=clamp(dot(normVal, lightDir),0.0,1.0);" +
                    "    if( uTextured==1 ) {" +
                    "        vCoord2 = vCoord;" +
                    "    }" +
                    "}";

    private final String fragmentShaderCode =
            "precision mediump float;" +
                    "uniform vec4 uColor;" +
                    "uniform vec4 vLightSource;" +    // Posicion global de la luz
                    "uniform mat4 uMVPMatrix;" + // matriz de transformacion
                    "uniform sampler2D vTex;" +
                    "varying vec2 vCoord2;" +
                    "varying float light;" +
                    "uniform lowp int uTextured;" +
                    "uniform lowp int uRigged;" +
                    "void main() {" +
                    "  float color=clamp(light+0.2,0.0,1.0);" +
                    "  if(uTextured==1)" +
                    "    gl_FragColor = color*texture2D(vTex, vCoord2);" +
                    "  else" +
                    "    gl_FragColor = vec4(vec3(uColor)*color,1.0);" +
                    //"  gl_FragColor = vec4(vec3(color),1.0);"+
                    "}";

    void draw() {
        activeSceneManager = this;
        ArrayList<Entity> objetos = new ArrayList<>();
        ArrayList<float[]> gpos = new ArrayList<>();

        // genera la photo de las entidades de la escena para usar en la rutina OPENGL
        scenePhoto.clear();
        // lista los objetos, recalcula cada accion y guarda posiciones (objeto y huesos)
        listNodes(root, root.getMatrixClone());

        viewPort.calcViewMatrix(mProjectionMatrix);
        // general settings
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LESS);
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glCullFace(GLES20.GL_BACK);
        GLES20.glFrontFace(GLES20.GL_CCW);
        // Fresh start, clear buffers
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        for (int i = 0; i < scenePhoto.size(); i++)
            if (scenePhoto.get(i).entityCopy.isVisible())
                drawMesh(scenePhoto.get(i).entityCopy, scenePhoto.get(i).rotPosCopy, scenePhoto.get(i).skeletonCopy);
    }

    public float deltaTime() {
        return deltatime;
    }

    public float updateDeltaTime() {
        long now = System.currentTimeMillis();
        //long now= SystemClock.uptimeMillis();
        deltatime = (now - time) / 1000.0f;
        time = now;
        Log.d("MyApp", "Delta: " + now + " " + deltatime);
        return deltatime;  //return dt>0.1?0.1f:dt;
    }

    public void resetTime() {
        time = System.currentTimeMillis() - 10;
    }

    // Lista los nodos para tener una copia FOTO que se pueda usar en la tutina OPENGL
    void listNodes(Node n, float[] pRotPos) {
        float[] scratch = new float[16];

        for (Sceneable c : n.children) {
            if (c.isEnabled()) {
                if (c.isDrawable()) {
                    Entity e = (Entity) c;
                    Matrix.multiplyMM(scratch, 0, pRotPos, 0, e.getMatrixClone(), 0);
                    e.addTime(deltatime);
                    float[] matHuesos = new float[16 * e.mesh.huesos.size()];
                    e.getAnimMatrix(matHuesos, true);  // no interpola entre frames, toma el mas cercano
                    scenePhoto.add(new EntityPhoto(e, scratch.clone(), matHuesos));
                    //objetos.add(((entity) c));
                    //matrices.add(scratch.clone());
                }
                if (c.isParent()) {
                    Matrix.multiplyMM(scratch, 0, pRotPos, 0, c.getMatrixClone(), 0);
                    listNodes((Node) c, scratch);
                }
            }
        }
    }

    void enablePhisics() {
        if (phy == null) phy = new Physics();
    }

    void disablePhisics() {
        phy = null;
    }

    void attachToPhisics(RigidFloor f) {
        if (phy != null) phy.pisos.add(f);
    }

    void setLightSource(float x, float y, float z) {
        lightSource[0] = x;
        lightSource[1] = y;
        lightSource[2] = z;
        lightSource[3] = 1;
    }

    public SceneManager(Camera cam) {
        viewPort = cam;
        root = new Node();
        time = System.currentTimeMillis();
        deltatime = 0;
        actionTime = 0;
        lightSource = new float[4];
        scenePhoto = new ArrayList<EntityPhoto>();

        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);

        // create empty OpenGL ES Program
        mProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(mProgram, vertexShader);
        GLES20.glAttachShader(mProgram, fragmentShader);
        GLES20.glLinkProgram(mProgram);

        if (GLES20.glGetError() != GLES20.GL_NO_ERROR)
            Log.d("MyApp", "Error linking shaders");
    }

    public Camera getViewPort() {
        return viewPort;
    }

    public void setViewPort(Camera cam) {
        viewPort = cam;
    }

    public int loadShader(int type, String shaderCode) {
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

    void checkGlError() {
        int err = GLES20.glGetError();
        boolean ind = GLES20.glIsProgram(mProgram);
        String str1 = GLES20.glGetProgramInfoLog(mProgram);

        if (err != GLES20.GL_NO_ERROR) {
            Log.d("MyApp", err + ":" + GLES20.glGetString(err));
        }
    }

    public void drawMesh(Entity e, float[] posRot, float[] skeletonMatices) {
        drawMesh(e, posRot, skeletonMatices, false);
    }

    public void load_glUniform4fv(int prg, String varName, float[] data) {
        int mHandler = GLES20.glGetUniformLocation(prg, varName);
        if (BuildConfig.DEBUG && mHandler < 0)
            throw new AssertionError("Error al cargar " + varName);
        glUniform4fv(mHandler, 1, data, 0);
    }

    public void load_glUniformMatrix4fv(int prg, String varName, float[] data, int size) {
        int mHandler = GLES20.glGetUniformLocation(prg, varName);
        if (BuildConfig.DEBUG && mHandler < 0)
            throw new AssertionError("Error al cargar " + varName);
        GLES20.glUniformMatrix4fv(mHandler, size, false, data, 0);
    }

    public void load_glUniform1i(int prg, String varName, int data) {
        int mHandler = GLES20.glGetUniformLocation(prg, varName);
        if (BuildConfig.DEBUG && mHandler < 0)
            throw new AssertionError("Error al cargar " + varName);
        GLES20.glUniform1i(mHandler, data);
    }

    public void load_glVertexAttribPointer(int prg, String varName, FloatBuffer data, boolean normalized, int size) {
        int mHandler = GLES20.glGetAttribLocation(prg, varName);
        if (BuildConfig.DEBUG && mHandler < 0)
            throw new AssertionError("Error al cargar " + varName);
        GLES20.glEnableVertexAttribArray(mHandler);
        GLES20.glVertexAttribPointer(mHandler, size, GLES20.GL_FLOAT, normalized, size * 4, data);
    }

    public void drawMesh(Entity e, float[] posRot, float[] skeletonMatrices, boolean isRigidBody) {
        Mesh m = e.mesh;
        boolean isRigged = m.skeleton;
        boolean isTextured = m.isTextured && !e.debug;

        for (SubMesh sr : e.mesh.subReticula)
        {
            // Add program to OpenGL ES environment
            GLES20.glUseProgram(mProgram);

            load_glUniform4fv(mProgram, "uColor", e.color);
            load_glUniform4fv(mProgram, "vLightSource", lightSource);
            load_glUniformMatrix4fv(mProgram, "uObjMatrix", posRot, 1);
            load_glUniform1i(mProgram, "uRigged", isRigged ? 1 : 0);
            load_glUniform1i(mProgram, "uTextured", isTextured ? 1 : 0);

            float[] mMVPMatrix = new float[16];
            Matrix.multiplyMM(mMVPMatrix, 0, viewPort.mVPMatrix, 0, posRot, 0);
            load_glUniformMatrix4fv(mProgram, "uMVPMatrix", mMVPMatrix, 1);
            load_glUniformMatrix4fv(mProgram, "uBone", skeletonMatrices, m.huesos.size());
            load_glVertexAttribPointer(mProgram, "vCoord", isTextured ? sr.uvBuffer : sr.vertexBuffer, true, 2);

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);                     // Set the active texture unit to texture unit 0.
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, m.textureHandle[0]); // Bind the texture to this unit.
            load_glUniform1i(mProgram, "vTex", 0);

            if (!e.debug)   // normal
            {
                load_glVertexAttribPointer(mProgram, "vPosition", sr.vertexBuffer, false, COORDS_PER_VERTEX);
                load_glVertexAttribPointer(mProgram, "vNormal", sr.normalBuffer, true, COORDS_PER_VERTEX);
                load_glVertexAttribPointer(mProgram, "vIndex", isRigged ? sr.boneBuffer : sr.vertexBuffer, true, 2);
                load_glVertexAttribPointer(mProgram, "vWeight", isRigged ? sr.weightBuffer : sr.vertexBuffer, true, 2);
                GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, sr.vertexCount);
            } else if (!isRigidBody)   // debug del objeto
            {
                load_glVertexAttribPointer(mProgram, "vPosition", sr._vertexBuffer, false, COORDS_PER_VERTEX);
                load_glVertexAttribPointer(mProgram, "vNormal", sr._normalBuffer, true, COORDS_PER_VERTEX);
                load_glVertexAttribPointer(mProgram, "vIndex", isRigged ? sr._boneBuffer : sr.vertexBuffer, true, 2);
                load_glVertexAttribPointer(mProgram, "vWeight", isRigged ? sr._weightBuffer : sr.vertexBuffer, true, 2);
                GLES20.glLineWidth(3.0f);
                GLES20.glDrawArrays(GLES20.GL_LINES, 0, sr._vertexCount);
            } else    // Espacio del cuerpo
            {
                load_glVertexAttribPointer(mProgram, "vPosition", ((RigidBody) e)._vertexBuffer, false, COORDS_PER_VERTEX);
                load_glVertexAttribPointer(mProgram, "vNormal", ((RigidBody) e)._normalBuffer, true, COORDS_PER_VERTEX);
                load_glVertexAttribPointer(mProgram, "vIndex", isRigged ? ((RigidBody) e)._boneBuffer : sr.vertexBuffer, true, 2);
                load_glVertexAttribPointer(mProgram, "vWeight", isRigged ? ((RigidBody) e)._weightBuffer : sr.vertexBuffer, true, 2);
                GLES20.glLineWidth(3.0f);
                GLES20.glDrawArrays(GLES20.GL_LINES, 0, ((RigidBody) e)._vertexCount);
            }

            // Debuguendo un esqueleto dinamico
            if (e.debug && e.isDinamic() && !isRigidBody) {
                if (!((RigidBody) e).OGL_Calc)
                    ((RigidBody) e).createOpenGlVars();
                drawMesh(e, posRot, skeletonMatrices, true);
            }
        }
    }
}

class SoundManager
{
    private static SoundManager instance;
    private SoundManager(){}

    //static block initialization for exception handling
    static{
        try{
            instance = new SoundManager();
            instance.init(5);
        }catch(Exception e){
            throw new RuntimeException("Exception occured in creating singleton instance");
        }
    }

    public static SoundManager getInstance(){
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
                id = soundPool.load(Ambiente.getInstance().getContext().getAssets().openFd(filename), 1);
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
class Camera extends Sceneable
{
    public float[] mVPMatrix = new float[16];          // world to screen view
    //public float[] mProjectionMatrix = new float[16];  // camera to screen view

    public Camera(float[] vojos, float[] vadelante, float[] varriba)
    {
        setLocation(vojos[0], vojos[1],vojos[2]);
        lookAt(vadelante,varriba);
        //calcViewMatrix();
    }

    public Camera()
    {
        setIdentity();
        //calcViewMatrix();
    }

    void calcViewMatrix(float[] ProjectionMatrix )
    {
        float[] scratch=new float[16];
        Matrix.invertM(scratch,0, getWorldMatrixClone(),0);
        Matrix.multiplyMM(mVPMatrix, 0, ProjectionMatrix, 0,scratch , 0);
    }
}

class Ambiente {

    private static Ambiente instance;

    private Ambiente(){}

    //static block initialization for exception handling
    static{
        try{
            instance = new Ambiente();
        }catch(Exception e){
            throw new RuntimeException("Exception occured in creating singleton instance");
        }
    }

    public static Ambiente getInstance(){
        return instance;
    }

    public Context contextCopy;
    public void setContext(Context c) {contextCopy=c;}
    public Context getContext() {return contextCopy;}
    public int displayw;
    public int displayh;
}

enum mode {oneTime, continous, stopped, boomerang}

class ActionState
{
    int actionId;
    mode looping;
    float blend;
    float time;
    float weight;
    Animation anim; // copia de la animacion

    // maneja los estdos de una animacion de un mesh determinado
    public ActionState(Mesh m, String name, mode loopMode, float blendTime, float startAt)
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
        // si hay que mezclar y queda todavia fuerza en la accion secundaria
        if(blend>0 && weight>0.0f)
            weight=Math.max(weight-dt/blend,0.0f);
        else
            weight=0;

        for(int i=0; i<anim.actionEvents.size();i++)
        {
            ActionEvent ae=anim.actionEvents.get(i);
            if( ae.kind==eventType.sound && time<ae.time && ae.time<time+dt)
                SoundManager.getInstance().playSound(ae.payload, 0.5f, 0.5f);
        }
        switch(looping)
        {
            case oneTime:
            {
                time+=dt;
                if(time>anim.timeLength)
                {
                    time=anim.timeLength;
                    looping=mode.stopped;
                }
                break;
            }
            case continous:
            {
                time+=dt;
                while(time>anim.timeLength)
                    time-=anim.timeLength;
                break;
            }
            case boomerang:
            {
                time+=dt;
                while(time>anim.timeLength)
                    time-=2*anim.timeLength;
                break;
            }
        }
        Log.d("MyApp", "Reel+ "+ time);
    }
}

// manejador de texturas con un catalogo
class ImageManager
{
    // Necesarias para el singleton
    private static ImageManager instance;
    private ImageManager()
    {
        catalogo=new HashMap<String,Integer>();
        bitmaps=new ArrayList<Bitmap>();

    }
    public static ImageManager getInstance(){ return instance; }

    //static block initialization for exception handling
    static{
        try{
            instance = new ImageManager();
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

            InputStream is= Ambiente.getInstance().getContext().getAssets().open(filename);
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