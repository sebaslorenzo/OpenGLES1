package com.example.admin.opengles1;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.support.annotation.NonNull;
import android.util.Log;
import android.opengl.Matrix;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static java.lang.Math.PI;

class Vertex
{
    public float[] co=new float[3];
    public ArrayList<VertexBoneWeight> pesos=new ArrayList<VertexBoneWeight>();
    public Vertex(float x, float y, float z) {co[0]=x;co[1]=y;co[2]=z;}
}

class SubMesh
{
    public String material;
    public List<Face> caras=new ArrayList<Face>();

    // variables Opengl para debugear
    public int vertexCount;
    public FloatBuffer vertexBuffer;
    public FloatBuffer normalBuffer;
    public FloatBuffer boneBuffer;
    public FloatBuffer weightBuffer;
    public FloatBuffer uvBuffer;

    // variables para debugear
    public int _vertexCount;
    public FloatBuffer _vertexBuffer;
    public FloatBuffer _normalBuffer;
    public FloatBuffer _boneBuffer;
    public FloatBuffer _weightBuffer;
    public FloatBuffer _uvBuffer;

    public SubMesh(String mat) {material=mat;}
}

class Face
{
    public int v1,v2,v3;
    public Face(int p1, int p2, int p3) {v1=p1;v2=p2;v3=p3;}

    public int getndx(int dim)
    {
        switch(dim) {
            case 0: return v1;
            case 1: return v2;
            case 2: return v3;
        }
        return 0;
    }
}

class UVcoord
{
    public float u,v;
    public UVcoord(float c1, float c2) {u=c1;v=c2;}
}

class Bone
{
    //public int id;
    String name;
    public float xCoord,yCoord,zCoord;
    public float xRot,yRot,zRot;
    public float angle;
    public int parent;
    public float[] restMatrixFromRoot;      // rotacion de pose final-compuesta desde el root
    public float[] invRestMatrixFromRoot;   // (inversa) rotacion de pose final-compuesta desde el root
    public float[] restMatrixFromParent;    // rotacion de pose del hueso respecto de su padre

    public Bone(int numero, String nombre)
    {
        //id=numero;
        name=nombre;
        restMatrixFromRoot =new float[16];
        invRestMatrixFromRoot =new float[16];
        restMatrixFromParent =new float[16];
        parent=-1;
    }
}

// definiciones tomadas de la configuracion
class KeyFrameDef
{
    Float time;
    public float xRot,yRot,zRot;    // vector de rotacion relativo al rest position
    public float angle;             // angulo de rotacion relativo al rest position
    public KeyFrameDef(float tiempo) {time=tiempo;xRot=yRot=zRot=xTra=yTra=zTra=0;xSca=ySca=zSca=1;}
    public float xTra, yTra, zTra;  // traslacion desde el rest position
    public float xSca, ySca, zSca;  // scale
}

class KeyFrame
{
    public float[] animMatrixFromRoot =new float[16];        // rotacion total compuesta desde el root
    // slerp equivalente a matriz, es relativa al hueso y se usa para mover mesh
    // se usan solo en los casos en que se interpola entre frames
    public float aSlerp;                        // keyframe interpolations, angle
    public float v1Slerp, v2Slerp, v3Slerp;     // keyframe interpolations, rotation vector
    public float xSlerp, ySlerp, zSlerp;        // keyframe interpolations, traslation
}

class Track
{
    public String name;
    int id;
    public float length;
    public ArrayList<KeyFrame> keyFrames;
    public ArrayList<KeyFrameDef> keyFrameDefs;
    public Track() {keyFrames=new ArrayList<KeyFrame>();keyFrameDefs=null;}
}

enum eventType {sound, message};

class ActionEvent {
    public eventType kind;
    public float time;
    public String payload;
}

class Animation
{
    String name;
    public float timeLength;        // largo en segundos
    public int numFrames;           // largo en frames
    public ArrayList<Track> tracks; // lista de huesos
    public int currentTrack;        // es el track que se esta usando en la carga del cfg
    public ArrayList<ActionEvent> actionEvents;

    public Animation(String nombre, float largo) {
        name=nombre;
        timeLength=largo;
        tracks=new ArrayList<Track>();
        actionEvents=new ArrayList<ActionEvent>();
    }
}

class VertexBoneWeight
{
    public int boneId;
    public float weight;
    public VertexBoneWeight(int b, float w) {boneId=b;weight=w;}
}

interface cadaTag
{
    void leerTag(XmlPullParser xpp);
}

class Rule
{
    public cadaTag callBack;
    String[] tokens;
    public Rule(String[] t, cadaTag cb) {tokens=t;callBack=cb;}
}

public class Mesh
{
    static ImageManager imageCatalog=null;

    List<Vertex> vertices=new ArrayList<Vertex>();
    List<Vertex> normales=new ArrayList<Vertex>();
    //List<Face> caras=new ArrayList<Face>();
    List<SubMesh> subReticula=new ArrayList<SubMesh>();
    List<UVcoord> UVmap=new ArrayList<UVcoord>();
    List<Bone> huesos=new ArrayList<Bone>();
    List<Animation> animaciones=new ArrayList<Animation>();
    boolean isTextured =false;
    public int textureSlot;  // en cual de los 8 (min) canales se guarda la textura

    //variables temporales
    public int[] textureHandle = new int[1];
    int lastAnimation;  // usado durante la carga de sonidos

    boolean skeleton=false;
    String skeletonName;
    public int totalVertexCount;
    public int totalFaceCount;

    private float[] scratch = new float[16];
    private float[] scratch2 = new float[16];

    // crea el mesh a partir de la lestura de uno o mas archivos Ogre3d
    public Mesh(String filename)
    {
        readMesh(filename+ ".mesh.xml");
        if(skeleton) {
            //readSkeleton(skeletonName + ".xml");
            readSkeleton(filename+".skeleton.xml");
            calcMatrices();
            readSkeletonEvents(filename+".event.xml");
            //dump(3,0,1);
            //dump2(3,0,1, 0.01f);
        }
        // Cargo la imagen para el mapa de bits
        if(isTextured)
            loadTexture(filename);

        createOpenGlVars();
    }

    FloatBuffer loadFloatBuffer(int size, float[] data)
    {
        ByteBuffer bb = ByteBuffer.allocateDirect(size);
        bb.order(ByteOrder.nativeOrder());

        // create a floating point buffer from the ByteBuffer
        FloatBuffer buffer = bb.asFloatBuffer();
        buffer.put(data);
        buffer.position(0);
        return buffer;
    }

    // Crea las variables base de OpenGL a partir de los datos leidos
    public void createOpenGlVars()
    {
        totalFaceCount=0;
        for(SubMesh sr:subReticula) {
            float[] triangleCoords;
            float[] normalCoords;
            float[] boneValues;
            float[] weightValues;
            float[] uvCoords = null;

            totalFaceCount+=sr.caras.size();

            triangleCoords = new float[sr.caras.size() * 3 * 3];
            normalCoords = new float[sr.caras.size() * 3 * 3];
            if (isTextured)
                uvCoords = new float[sr.caras.size() * 3 * 2];

            for (int i = 0; i < sr.caras.size(); i++)   // caras del objeto
            {
                for (int j = 0; j < 3; j++)   //Puntos de la cara
                {
                    for (int k = 0; k < 3; k++)   // Coords del punto
                    {
                        triangleCoords[i * 9 + j * 3 + k] = vertices.get(sr.caras.get(i).getndx(j)).co[k];
                        normalCoords[i * 9 + j * 3 + k] = normales.get(sr.caras.get(i).getndx(j)).co[k];
                    }
                    if (isTextured) {
                        uvCoords[i * 6 + j * 2] = UVmap.get(sr.caras.get(i).getndx(j)).u;
                        uvCoords[i * 6 + j * 2 + 1] = UVmap.get(sr.caras.get(i).getndx(j)).v;
                    }
                }
            }

            // initialize vertex byte buffer for shape coordinates, // 3 puntos, 3 coords, 4 del float
            sr.vertexBuffer = loadFloatBuffer(sr.caras.size() * 9 * 4, triangleCoords);
            sr.normalBuffer = loadFloatBuffer(sr.caras.size() * 9 * 4, normalCoords);
            sr.vertexCount = sr.caras.size() * 3;

            if (skeleton) {
                boneValues = new float[sr.caras.size() * 6];   // 3 puntos 2 datos
                weightValues = new float[sr.caras.size() * 6];
                for (int i = 0; i < boneValues.length; i++)   // valores
                {
                    boneValues[i] = 0;
                    weightValues[i] = 0;
                }
                for (int i = 0; i < sr.caras.size(); i++)   // caras del objeto
                {
                    Face c = sr.caras.get(i);
                    for (int j = 0; j < 3; j++)   //Puntos de la cara
                    {
                        Vertex v = vertices.get(c.getndx(j));

                        float totalW = 0;
                        for (int k = 0; k < 2 && k < v.pesos.size(); k++)
                            totalW += v.pesos.get(k).weight;

                        for (int k = 0; k < 2 && k < v.pesos.size(); k++)   //max 2 huesos
                        {
                            VertexBoneWeight vw = v.pesos.get(k);
                            boneValues[i * 6 + j * 2 + k] = vw.boneId;
                            weightValues[i * 6 + j * 2 + k] = vw.weight / totalW;
                        }
                    }
                }

                // 3 puntos, 2 huesos, 4 del float
                sr.boneBuffer = loadFloatBuffer(sr.caras.size() * 6 * 4, boneValues);
                sr.weightBuffer = loadFloatBuffer(sr.caras.size() * 6 * 4, weightValues);
            }
            if (isTextured) {
                // 3 puntos, 2 coords, 4 del float
                sr.uvBuffer = loadFloatBuffer(sr.caras.size() * 6 * 4, uvCoords);
            }

            // --------------------------------- Debugging data -----------------------------------------
            float[] _lineCoords;
            float[] _normalCoords;
            float[] _boneValues;
            float[] _weightValues;

            if (!skeleton) {
                _lineCoords = new float[sr.caras.size() * 3 * 2 * 3];  // caras * 3 vertices * 2 puntos * 3 coordenadas
                _normalCoords = new float[sr.caras.size() * 3 * 2 * 3];

                for (int i = 0; i < sr.caras.size(); i++)   // caras del objeto
                {
                    Face c = sr.caras.get(i);
                    for (int j = 0; j < 3; j++)   // Puntos de la cara
                    {
                        Vertex v1 = vertices.get(c.getndx(j));
                        Vertex v2 = vertices.get(c.getndx((j + 1) % 3));

                        for (int k = 0; k < 3; k++)   // Coords del punto
                        {
                            _lineCoords[i * 18 + j * 6 + k] = v1.co[k];
                            _lineCoords[i * 18 + j * 6 + k + 3] = v2.co[k];
                            _normalCoords[i * 18 + j * 6 + k] = k % 2;    //0,1,0
                            _normalCoords[i * 18 + j * 6 + k + 3] = k % 2;
                        }
                    }
                }

                // 3 vertices, 2 puntos, 3 coords, 4 del float
                sr._vertexBuffer = loadFloatBuffer(sr.caras.size() * 3 * 2 * 3 * 4, _lineCoords);
                sr._normalBuffer = loadFloatBuffer(sr.caras.size() * 3 * 2 * 3 * 4, _normalCoords);
                sr._vertexCount = sr.caras.size() * 3 * 2;
            } else {
                float[] v0 = {0, 0, 0, 1};
                float[] v1 = {0, 0.5f, 0, 1};
                float[] s = new float[4];

                _boneValues = new float[huesos.size() * 2 * 2];   // 2 puntos 2 datos
                _weightValues = new float[huesos.size() * 2 * 2];

                _lineCoords = new float[huesos.size() * 2 * 3];  // huesos * dos puntos * 3 coordenadas
                _normalCoords = new float[huesos.size() * 2 * 3];

                for (int i = 0; i < huesos.size(); i++)   // huesos del skeleton
                {
                    Matrix.multiplyMV(s, 0, huesos.get(i).restMatrixFromRoot, 0, v0, 0);
                    _lineCoords[i * 6] = s[0];
                    _lineCoords[i * 6 + 1] = s[1];
                    _lineCoords[i * 6 + 2] = s[2];
                    Matrix.multiplyMV(s, 0, huesos.get(i).restMatrixFromRoot, 0, v1, 0);
                    _lineCoords[i * 6 + 3] = s[0];
                    _lineCoords[i * 6 + 4] = s[1];
                    _lineCoords[i * 6 + 5] = s[2];

                    for (int j = 0; j < 6; j++)   // todos miran a 1,1,1
                        _normalCoords[i * 6 + j] = j % 2;

                    for (int j = 0; j < 2; j++)   // cada punta del hueso
                    {
                        _boneValues[i * 4 + j * 2] = i;
                        _weightValues[i * 4 + j * 2] = 1;
                        _boneValues[i * 4 + j * 2 + 1] = 0;
                        _weightValues[i * 4 + +j * 2 + 1] = 0;
                    }
                }

                // 2 puntos, 3 coords, 4 del float
                sr._vertexBuffer = loadFloatBuffer(huesos.size() * 2 * 3 * 4, _lineCoords);
                sr._normalBuffer = loadFloatBuffer(huesos.size() * 2 * 3 * 4, _normalCoords);

                sr._boneBuffer = loadFloatBuffer(huesos.size() * 4 * 4, _boneValues);
                sr._weightBuffer = loadFloatBuffer(sr.caras.size() * 4 * 4, _weightValues);
                sr._vertexCount = huesos.size() * 2;
            }
        }
    }

    private void loadTexture(String imageName)
    {
        textureSlot= ImageManager.getInstance().loadImage(imageName+".png");

        Bitmap bitm= ImageManager.getInstance().getImage(textureSlot);

        // Genera texture name (1 solo aca)  que son devueltos en textureHandle.
        // glGenTextures(Cantidad a generar, handlers, offset)
        GLES20.glGenTextures(1, textureHandle, 0);

        if (textureHandle[0] != 0)
        {
            // Bind to the texture in OpenGL to a specific type (2D),
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0]);

            // Set filtering
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
            //GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            //GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

            // Load the bitmap into the bound texture.
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitm, 0);
        }

        if (textureHandle[0] == 0)
            throw new RuntimeException("Error loading texture.");
    }

    // lee un archivo Ogre con la info de los puntos, las caras, los huesos y las coordenadas UV
    public void readMesh(String filename)
    {
        ArrayList<String> uri=new ArrayList<String>();
        ArrayList<Rule> reglas = new ArrayList<Rule>();
        reglas.add(new Rule(
            new String[]{"mesh", "sharedgeometry"},
            new cadaTag() {
                public void leerTag(XmlPullParser xpp) {
                    Log.d("myTag", "vertexcount");
                    totalVertexCount=Integer.parseInt(xpp.getAttributeValue(null, "vertexcount"));
                }
            }
        ));

        reglas.add(new Rule(
                new String[]{"mesh", "sharedgeometry", "vertexbuffer", "vertex", "position"},
                new cadaTag() {
                    public void leerTag(XmlPullParser xpp) {
                        Log.d("myTag", "vertice");
                        float x = Float.parseFloat(xpp.getAttributeValue(null, "x"));
                        float y = Float.parseFloat(xpp.getAttributeValue(null, "y"));
                        float z = Float.parseFloat(xpp.getAttributeValue(null, "z"));
                        vertices.add(new Vertex(x, y, z));
                    }
                }
        ));

        reglas.add(new Rule(
            new String[]{"mesh", "sharedgeometry", "vertexbuffer", "vertex", "normal"},
            new cadaTag() {
                public void leerTag(XmlPullParser xpp) {
                    Log.d("myTag", "normal");
                    float x = Float.parseFloat(xpp.getAttributeValue(null, "x"));
                    float y = Float.parseFloat(xpp.getAttributeValue(null, "y"));
                    float z = Float.parseFloat(xpp.getAttributeValue(null, "z"));
                    normales.add(new Vertex(x, y, z));
                }
            }
        ));

        reglas.add(new Rule(
                new String[]{"mesh", "sharedgeometry", "vertexbuffer"},
                new cadaTag() {
                    public void leerTag(XmlPullParser xpp) {
                        Log.d("myTag", "textured");
                        isTextured = Integer.parseInt(xpp.getAttributeValue(null, "texture_coords"))==1;
                    }
                }
        ));

        reglas.add(new Rule(
                new String[]{"mesh", "submeshes", "submesh"},
                new cadaTag() {
                    public void leerTag(XmlPullParser xpp) {
                        Log.d("myTag", "submesh");
                        subReticula.add(new SubMesh(xpp.getAttributeValue(null, "material")));
                    }
                }
        ));

        reglas.add(new Rule(
                new String[]{"mesh", "submeshes", "submesh", "faces"},
                new cadaTag() {
                    public void leerTag(XmlPullParser xpp) {
                        Log.d("myTag", "count");
                        subReticula.get(subReticula.size()-1).vertexCount=Integer.parseInt(xpp.getAttributeValue(null, "count"));
                    }
                }
        ));

        reglas.add(new Rule(
            new String[]{"mesh", "submeshes", "submesh", "faces", "face"},
            new cadaTag() {
                public void leerTag(XmlPullParser xpp) {
                    Log.d("myTag", "cara");
                    int v1 = Integer.parseInt(xpp.getAttributeValue(null, "v1"));
                    int v2 = Integer.parseInt(xpp.getAttributeValue(null, "v2"));
                    int v3 = Integer.parseInt(xpp.getAttributeValue(null, "v3"));
                    //caras.add(new Face(v1, v3, v2));
                    subReticula.get(subReticula.size()-1).caras.add(new Face(v1, v2, v3));
                }
            }
        ));

        reglas.add(new Rule(
            new String[]{"mesh", "sharedgeometry", "vertexbuffer", "vertex", "texcoord"},
            new cadaTag() {
                public void leerTag(XmlPullParser xpp) {
                    Log.d("myTag", "UV");
                    float u = Float.parseFloat(xpp.getAttributeValue(null, "u"));
                    float v = Float.parseFloat(xpp.getAttributeValue(null, "v"));
                    UVmap.add(new UVcoord(u, v));
                }
            }
        ));

        reglas.add(new Rule(
                new String[]{"mesh", "boneassignments", "vertexboneassignment"},
                new cadaTag() {
                    public void leerTag(XmlPullParser xpp) {
                        Log.d("myTag", "weight");
                        int v = Integer.parseInt(xpp.getAttributeValue(null, "vertexindex"));
                        int b = Integer.parseInt(xpp.getAttributeValue(null, "boneindex"));
                        float w = Float.parseFloat(xpp.getAttributeValue(null, "weight"));
                        vertices.get(v).pesos.add(new VertexBoneWeight(b,w));
                    }
                }
        ));

        reglas.add(new Rule(
                new String[]{"mesh", "skeletonlink"},
                new cadaTag() {
                    public void leerTag(XmlPullParser xpp) {
                        Log.d("myTag", "skeleton");
                        skeletonName=xpp.getAttributeValue(null, "name");
                        skeleton=true;
                    }
                }
        ));

        readXml(filename, reglas, uri);
    }

    // lee un archivo Ogre con la info de las anaimaciones
    public void readSkeleton(String filename)
    {
        ArrayList<String> uri=new ArrayList<String>();
        ArrayList<Rule> reglas=new ArrayList<Rule>();
        reglas.add(new Rule(
            new String[]{"skeleton","bones","bone"},
            new cadaTag() {
                public void leerTag(XmlPullParser xpp)
                {
                    Log.d("myTag", "bone");
                    int b = Integer.parseInt(xpp.getAttributeValue(null, "id"));
                    String nombre=xpp.getAttributeValue(null, "name");
                    huesos.add(new Bone(b, nombre));
                }
            }
        ));

        reglas.add(new Rule(
            new String[]{"skeleton","bones","bone","position"},
            new cadaTag() {
                public void leerTag(XmlPullParser xpp)
                {
                    Log.d("myTag", "bone/position");
                    Bone lastbone=huesos.get(huesos.size()-1);
                    lastbone.xCoord = Float.parseFloat(xpp.getAttributeValue(null, "x"));
                    lastbone.yCoord = Float.parseFloat(xpp.getAttributeValue(null, "y"));
                    lastbone.zCoord = Float.parseFloat(xpp.getAttributeValue(null, "z"));
                }
            }
        ));

        reglas.add(new Rule(
            new String[]{"skeleton","bones","bone","rotation"},
            new cadaTag() {
                public void leerTag(XmlPullParser xpp)
                {
                    Log.d("myTag", "bone/rotation");
                    Bone lastbone=huesos.get(huesos.size()-1);
                    lastbone.angle = Float.parseFloat(xpp.getAttributeValue(null, "angle"));
                }
            }
        ));

        reglas.add(new Rule(
            new String[]{"skeleton","bones","bone","rotation","axis"},
            new cadaTag() {
                public void leerTag(XmlPullParser xpp)
                {
                    Log.d("myTag", "bone/rotation/axis");
                    Bone lastbone=huesos.get(huesos.size()-1);
                    lastbone.xRot = Float.parseFloat(xpp.getAttributeValue(null, "x"));
                    lastbone.yRot = Float.parseFloat(xpp.getAttributeValue(null, "y"));
                    lastbone.zRot = Float.parseFloat(xpp.getAttributeValue(null, "z"));
                }
            }
        ));

        reglas.add(new Rule(
            new String[]{"skeleton","bonehierarchy","boneparent"},
            new cadaTag() {
                public void leerTag(XmlPullParser xpp)
                {
                    Log.d("myTag", "bone/bonehierarchy");
                    String child=xpp.getAttributeValue(null, "bone");
                    String padre=xpp.getAttributeValue(null, "parent");
                    int childId=0;
                    int padreId=0;

                    for(int i=0;i<huesos.size();i++)
                    {
                        if(huesos.get(i).name.equals(child))
                            childId=i;
                        if(huesos.get(i).name.equals(padre))
                            padreId=i;
                    }
                    huesos.get(childId).parent=padreId;
                }
            }
        ));

        reglas.add(new Rule(
            new String[]{"skeleton","animations","animation"},
            new cadaTag() {
                public void leerTag(XmlPullParser xpp)
                {
                    Log.d("myTag", "animation");
                    String nombre=xpp.getAttributeValue(null, "name");
                    float largo=Float.parseFloat(xpp.getAttributeValue(null, "length"));
                    Animation thisAnim=new Animation(nombre,largo);
                    animaciones.add(thisAnim);
                    // crear los tracks necesarios para cada hueso
                    for(int i=0;i<huesos.size();i++)
                        thisAnim.tracks.add(new Track());
                }
            }
        ));

        reglas.add(new Rule(
            new String[]{"skeleton","animations","animation","tracks","track"},
            new cadaTag() {
                public void leerTag(XmlPullParser xpp)
                {
                    Log.d("myTag", "track");
                    String hueso=xpp.getAttributeValue(null, "bone");
                    int boneIndex=0;
                    for(int i=0;i<huesos.size();i++)
                    {
                        if (huesos.get(i).name.equals(hueso)) {
                            Animation lastAnim=animaciones.get(animaciones.size()-1);
                            lastAnim.currentTrack = i; // Número de entrada en la lista de huesos
                            lastAnim.tracks.get(i).keyFrameDefs=new ArrayList<KeyFrameDef>();
                        }
                    }
                }
            }
        ));

        reglas.add(new Rule(
            new String[]{"skeleton","animations","animation","tracks","track","keyframes","keyframe"},
            new cadaTag() {
                public void leerTag(XmlPullParser xpp)
                {
                    Log.d("myTag", "keyframe");
                    Animation lastAnim=animaciones.get(animaciones.size()-1);
                    Track lastTrack=lastAnim.tracks.get(lastAnim.currentTrack);
                    float tiempo=Float.parseFloat(xpp.getAttributeValue(null, "time"));
                    lastTrack.keyFrameDefs.add(new KeyFrameDef(tiempo));
                }
            }
        ));

        reglas.add(new Rule(
            new String[]{"skeleton","animations","animation","tracks","track","keyframes","keyframe","rotate"},
            new cadaTag() {
                public void leerTag(XmlPullParser xpp)
                {
                    Log.d("myTag", "bone/rotate");
                    Animation lastAnim=animaciones.get(animaciones.size()-1);
                    Track lastTrack=lastAnim.tracks.get(lastAnim.currentTrack);
                    KeyFrameDef lastKeyFrame=lastTrack.keyFrameDefs.get(lastTrack.keyFrameDefs.size()-1);  // error
                    lastKeyFrame.angle=Float.parseFloat(xpp.getAttributeValue(null, "angle"));
                }
            }
        ));

        reglas.add(new Rule(
            new String[]{"skeleton","animations","animation","tracks","track","keyframes","keyframe","rotate","axis"},
            new cadaTag() {
                public void leerTag(XmlPullParser xpp)
                {
                    Log.d("myTag", "bone/rotate");
                    Animation lastAnim=animaciones.get(animaciones.size()-1);
                    Track lastTrack=lastAnim.tracks.get(lastAnim.currentTrack);
                    KeyFrameDef lastKeyFrame=lastTrack.keyFrameDefs.get(lastTrack.keyFrameDefs.size()-1);
                    lastKeyFrame.xRot=Float.parseFloat(xpp.getAttributeValue(null, "x"));
                    lastKeyFrame.yRot=Float.parseFloat(xpp.getAttributeValue(null, "y"));
                    lastKeyFrame.zRot=Float.parseFloat(xpp.getAttributeValue(null, "z"));
                }
            }
        ));

        reglas.add(new Rule(
                new String[]{"skeleton","animations","animation","tracks","track","keyframes","keyframe","translate"},
                new cadaTag() {
                    public void leerTag(XmlPullParser xpp)
                    {
                        Log.d("myTag", "bone/translate");
                        Animation lastAnim=animaciones.get(animaciones.size()-1);
                        Track lastTrack=lastAnim.tracks.get(lastAnim.currentTrack);
                        KeyFrameDef lastKeyFrame=lastTrack.keyFrameDefs.get(lastTrack.keyFrameDefs.size()-1);
                        lastKeyFrame.xTra=Float.parseFloat(xpp.getAttributeValue(null, "x"));
                        lastKeyFrame.yTra=Float.parseFloat(xpp.getAttributeValue(null, "y"));
                        lastKeyFrame.zTra=Float.parseFloat(xpp.getAttributeValue(null, "z"));
                    }
                }
        ));

        reglas.add(new Rule(
                new String[]{"skeleton","animations","animation","tracks","track","keyframes","keyframe","scale"},
                new cadaTag() {
                    public void leerTag(XmlPullParser xpp)
                    {
                        Log.d("myTag", "bone/scale");
                        Animation lastAnim=animaciones.get(animaciones.size()-1);
                        Track lastTrack=lastAnim.tracks.get(lastAnim.currentTrack);
                        KeyFrameDef lastKeyFrame=lastTrack.keyFrameDefs.get(lastTrack.keyFrameDefs.size()-1);
                        lastKeyFrame.xSca=Float.parseFloat(xpp.getAttributeValue(null, "x"));
                        lastKeyFrame.ySca=Float.parseFloat(xpp.getAttributeValue(null, "y"));
                        lastKeyFrame.zSca=Float.parseFloat(xpp.getAttributeValue(null, "z"));
                    }
                }
        ));

        readXml(filename, reglas, uri);
    }

    // lee un archivo con la info de los sonidos de la animacion
    public void readSkeletonEvents(String filename)
    {
        ArrayList<String> uri=new ArrayList<String>();
        ArrayList<Rule> reglas=new ArrayList<Rule>();
        lastAnimation=0;

        reglas.add(new Rule(
                new String[]{"animations","animation"},
                new cadaTag() {
                    public void leerTag(XmlPullParser xpp)
                    {
                        Log.d("myTag", "animation");
                        String nombre=xpp.getAttributeValue(null, "name");
                        for(int i=0; i<animaciones.size(); i++)
                            if(animaciones.get(i).name.equals(nombre))
                                lastAnimation=i;
                            }
                }
        ));

        reglas.add(new Rule(
                new String[]{"animations","animation","events","sound"},
                new cadaTag() {
                    public void leerTag(XmlPullParser xpp)
                    {
                        Log.d("myTag", "animation/sound");
                        float time=Float.parseFloat(xpp.getAttributeValue(null, "time"));
                        String filename=xpp.getAttributeValue(null, "payload");
                        ActionEvent ae=new ActionEvent();
                        ae.kind=eventType.sound;
                        ae.payload=filename;
                        ae.time=time;
                        animaciones.get(lastAnimation).actionEvents.add(ae);
                    }
                }
        ));

        // el archivo de sonido es opcional
        try {
            readXml(filename, reglas, uri);
        } catch(Exception e) {
            System.err.print("File "+filename+" not found.");
        }
    }

    // Calcula las matrices de cada frame para el esqueleto recien cargado
    public void calcMatrices()
    {
        float[] idMat= new float[16];
        Matrix.setIdentityM(idMat,0);
        float[] pm;
        ArrayList<Integer> todo=new ArrayList<Integer>();
        int bId;

        // primero analiza la matriz de cada hueso en rest position
        todo.add(-1);   //padres analizados, arranca con los root que tienen padre -1
        while(todo.size()>0) {
            bId = todo.get(0);
            todo.remove(0);
            for( int bi=0; bi<huesos.size(); bi++) {
                Bone b=huesos.get(bi);  // para cada entrada de los huesos (bone Index)
                if(b.parent == bId) {   // b es el hijo
                    pm = idMat;
                    if (b.parent != -1) // tiene padre
                        pm = huesos.get(b.parent).restMatrixFromRoot;
                    if(b.angle==0)
                        b.restMatrixFromParent =idMat.clone();
                    else
                        Matrix.setRotateM(b.restMatrixFromParent, 0, b.angle * 180 / (float) PI, b.xRot, b.yRot, b.zRot);
                    b.restMatrixFromParent[12] = b.xCoord;
                    b.restMatrixFromParent[13] = b.yCoord;
                    b.restMatrixFromParent[14] = b.zCoord;
                    Matrix.multiplyMM(b.restMatrixFromRoot, 0, pm, 0, b.restMatrixFromParent, 0);
                    Matrix.invertM(b.invRestMatrixFromRoot, 0, b.restMatrixFromRoot,0);
                    todo.add(bi);
                }
            }
        }

        // despues analizo cada animacion para calcular la matriz en cada keyframe/track
        for( Animation a : animaciones) {
            a.numFrames = a.tracks.get(a.currentTrack).keyFrameDefs.size();

            // creo los keyframes vacios de cada hueso de la animacion
            for( int h=0; h<huesos.size(); h++) {
                Track t=a.tracks.get(h);
                t.id=h;
                // creo los keyFrames
                for( int i=0; i<a.numFrames; i++)
                    t.keyFrames.add(new KeyFrame());
            }

            todo.add(-1);   //padres analizados, arranca con los root que tienen padre -1
            while (todo.size() > 0) {
                bId = todo.get(0);
                todo.remove(0);
                for( int h=0; h<huesos.size(); h++) {
                    Bone b = huesos.get(h);
                    if (b.parent == bId) {
                        Track t=a.tracks.get(h);
                        for (int fn=0; fn<t.keyFrames.size();fn++) {
                            KeyFrame kf=t.keyFrames.get(fn);
                            pm = idMat;
                            float tx, ty, tz, sx, sy, sz; // de la accion
                            tx=ty=tz=0;
                            sx=sy=sz=1;

                            if (b.parent != -1)
                                pm = a.tracks.get(bId).keyFrames.get(fn).animMatrixFromRoot;

                            if( t.keyFrameDefs==null)
                                scratch2 = idMat.clone();
                            else {
                                KeyFrameDef kfd = t.keyFrameDefs.get(fn);
                                if (kfd.angle == 0)
                                    scratch2 = idMat.clone();
                                else
                                    Matrix.setRotateM(scratch2, 0, kfd.angle * 180 / (float) PI, kfd.xRot, kfd.yRot, kfd.zRot);
                                tx=kfd.xTra;  // translation: antes o despues???
                                ty=kfd.yTra;
                                tz=kfd.zTra;
                                sx=kfd.xSca;  // scale: antes o despues???
                                sy=kfd.ySca;
                                sz=kfd.zSca;
                            }

                            // mat rel del hueso=mat rel en pose * movimiento en el frame
                            Matrix.multiplyMM(scratch, 0, b.restMatrixFromParent, 0, scratch2, 0);
                            Matrix.scaleM(scratch,0,sx,sy,sz);
                            scratch[12]+=tx;
                            scratch[13]+=ty;
                            scratch[14]+=tz;

                            // mat final-compuesta del hueso=mat del padre*mat rel hueso
                            Matrix.multiplyMM(kf.animMatrixFromRoot, 0, pm, 0, scratch, 0);
                        }
                        todo.add(t.id);
                    }
                }
            }
        }

        for( Animation a : animaciones) {
            for (Track t : a.tracks) {
                for (int fn=0; fn<t.keyFrames.size()-1;fn++) {
                    KeyFrame kf1=t.keyFrames.get(fn);
                    KeyFrame kf2=t.keyFrames.get(fn+1);
                    Matrix.invertM(scratch2,0,kf1.animMatrixFromRoot,0);
                    Matrix.multiplyMM(scratch, 0, scratch2, 0, kf2.animMatrixFromRoot, 0);
                    double angle=safeacos((scratch[0]+scratch[5]+scratch[10]-1)/2); // Radians
                    // vector y angulo para interpolar ambas rotaciones
                    kf1.aSlerp=(float)(angle* 180 / (float) PI);  // Degrees
                    double divisor=2*Math.sin(angle);
                    kf1.v1Slerp=(float)((scratch[6]-scratch[9])/divisor);
                    kf1.v2Slerp=(float)((scratch[8]-scratch[2])/divisor);
                    kf1.v3Slerp=(float)((scratch[1]-scratch[4])/divisor);
                    // traslation
                    kf1.xSlerp=scratch[12];
                    kf1.ySlerp=scratch[13];
                    kf1.zSlerp=scratch[14];
                }
            }
        }
    }

    // Dados dos frames consecutivos calcula la matriz de un tiempo intermedio
    // matriz relativa al objeto
    public void interMatrix(float[] output, int offset, int animId, int boneId, float frameTime, boolean highDef)
    {
        Animation a=animaciones.get(animId);
        Track tr=a.tracks.get(boneId);

        if(tr!=null)    // hay data para el hueso, se mueve!!!
        {
            if(highDef) {
                int fn = (int) frameTime;    // Frame number
                float ii = frameTime - fn;     // range[0,1)

                KeyFrame kf = tr.keyFrames.get(fn);
                if (Math.abs(kf.aSlerp) > 0.00001)
                    Matrix.setRotateM(scratch, 0, kf.aSlerp * (float) ii, kf.v1Slerp, kf.v2Slerp, kf.v3Slerp);
                else
                    Matrix.setIdentityM(scratch, 0);
                scratch[12] = kf.xSlerp * ii;
                scratch[13] = kf.ySlerp * ii;
                scratch[14] = kf.zSlerp * ii;
                Matrix.multiplyMM(output, offset, kf.animMatrixFromRoot, 0, scratch, 0);
            }
            else
                System.arraycopy(a.tracks.get(boneId).keyFrames.get(Math.round(frameTime)).animMatrixFromRoot, 0, output, offset, 16);
            //Log.d("MyApp", "time:" + String.valueOf(rt)+outMat(output,offset));
        }
        else    // ningun movimiento para este hueso
            System.arraycopy(huesos.get(boneId).restMatrixFromRoot, 0, output, offset, 16);
    }

    String outMat(float[] mat, int offset)
    {
        StringBuilder tmp= new StringBuilder();
        for(int i=0; i<16; i++)
        {
            tmp.append(";").append(mat[offset + i]);
        }
        return tmp.toString();
    }

    // va calculando el URI del elemento actual del XML
    private void calcUri(ArrayList<String> uri, XmlPullParser xpp) {
        try {
            int eventType = xpp.getEventType();

            if (eventType == XmlPullParser.START_TAG)
                uri.add(xpp.getName());
            else if (eventType == XmlPullParser.END_TAG)
                uri.remove(xpp.getDepth() - 1);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // chequea si el URI actual es uno dado
    private boolean checkURI(String[] test, ArrayList<String> uri)
    {
        if( uri.size()!=test.length)
            return false;

        for(int i=0; i<uri.size(); i++)
        {
            if(!test[i].equals(uri.get(i)))
                return false;
        }
        return true;
    }

    // lee un XML y va comparando cada elemento con las reglas para llamar a la funcion que lo procesa
    private void readXml(String filename, ArrayList<Rule> rules, ArrayList<String> uri)
    {
        XmlPullParserFactory factory;
        XmlPullParser xpp;

        try
        {
            factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
            xpp = factory.newPullParser();
            InputStream is= Ambiente.getInstance().getContext().getAssets().open(filename);
            InputStreamReader isr=new InputStreamReader(is);
            xpp.setInput(isr);

            int eventType = xpp.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                calcUri(uri, xpp);
                if(xpp.getEventType()==XmlPullParser.START_TAG) {
                    for (int t = 0; t < rules.size(); t++) {
                        if (checkURI(rules.get(t).tokens, uri)) {
                            rules.get(t).callBack.leerTag(xpp);
                        }
                    }
                }
                eventType = xpp.next();
            }
            isr.close();
            is.close();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    // se asegura el rango de entrada para que no devuelve NaN
    private double safeacos(double x)
    {
        return Math.acos(Math.max(Math.min(x,1),-1));
    }

    int getBoneIdByName(String name)
    {
        for(int i=0;i<huesos.size(); i++) {
            if (huesos.get(i).name.equals(name))
                return i;
        }
        return -1;
    }
}

// manejador de meshes con un catalogo
class MeshManager
{
    HashMap<String, Mesh> catalogo=new HashMap<>();

    private Mesh loadMesh(String filename)
    {
        Mesh m=new Mesh(filename);
        catalogo.put(filename, m);
        return m;
    }

    public Mesh getMesh(String filename)
    {
        if(catalogo.containsKey(filename))
            return (Mesh)catalogo.get(filename);
        return loadMesh(filename);
    }
}

class ActionInstruction
{
    public String accion;
    public mode modo;
    public float blend;
    public float startAt;
    public ActionInstruction(String _accion, mode _modo, float _blend, float _startAt)
    {
        accion=_accion;
        modo=_modo;
        blend=_blend;
        startAt=_startAt;
    }
}

class Entity extends Node
{
    static MeshManager meshCatalog=null;
    Mesh mesh;
    public ArrayList<ActionState> actionList;
    public ArrayList<ActionInstruction> actionInstructionList;
    public boolean debug=false;
    public float[] color = new float[4];
    private boolean visible=true;
    int id=0;

    public Entity(String name)
    {
        setIdentity();
        if( meshCatalog==null)
            meshCatalog=new MeshManager();
        mesh=meshCatalog.getMesh(name);
        actionList=new ArrayList<ActionState>();
        actionInstructionList=new ArrayList<ActionInstruction>();
        color[0]=color[1]=color[2]=color[3]=1.0f;   // default white color
    }

    // ahora es seguro hacer la destruccion?
    public void destroy()
    {
        // deshacer parenthood
        for(Sceneable s:children)
            s.removeParent();
        // deshacer childhood
        removeParent();
        actionList=null;
        actionInstructionList=null;
    }

    public void setId(int _id) {id=_id;}
    public int getId() {return id;}
    void setVisible(boolean v) {visible=v;}
    boolean isVisible() {return visible;}

    // inicia una accion, agregandola a la lista de acciones que se ejecutan
    // los parametros se agregan a una lista para ser impactados cuando sea seuro
    public void playAction(String accion, mode modo, float blend, float startAt) {
        ActionInstruction ai = new ActionInstruction(accion, modo, blend, startAt);
        actionInstructionList.add(ai);
    }

    // Impacta las acciones estackeadas anteriormente. Ahora deberia ser seguro porque no
    // se estan usando prmitivas de OPENGL para dibujar la pantalla
    private void applyNewActions()
    {
        for( ActionInstruction ai : actionInstructionList ) {
            ActionState a = new ActionState(mesh, ai.accion, ai.modo, ai.blend, ai.startAt);
            if (a.looping != mode.stopped) {
                if (actionList.size() > 0)     // la accion anterior se debe morir en ese tiempo
                    actionList.get(actionList.size() - 1).blend = ai.blend;
                actionList.add(a);
            }
        }
        actionInstructionList.clear();
    }

    // agrega un tiempo dado a cada accion de la lista de acciones
    // si alguna llego a WEIGHT 0 entonces esa y las anteriores se extinguieron, las elimino
    void addTime(float dt)
    {
        applyNewActions();  // si algna accion estaba esperando se aplica
        boolean remover=false;
        for(int i=actionList.size()-1;i>=0; i--) {
            if (remover)
                actionList.remove(i);
            else
            {
                actionList.get(i).addTime(dt);
                // si llego el weight a cero elimino anteriores
                if (actionList.get(i).weight <= 0)
                    remover = true;
            }
        }
    }

    // devuelve el tiempo de la ultima accion ejecutandose
    // sirve para sincronizar alguna nueva accion
    public float getAminTime()
    {
        return actionList.size()==0?0:actionList.get(actionList.size() - 1).time;
    }

    public String getAminDesc()
    {
        StringBuilder rs= new StringBuilder("(" + actionList.size() + "):");
        for( ActionState as:actionList )
            rs.append(as.anim.name).append(":").append(as.looping).append(":").append(as.time).append(":").append(as.weight).append(";");

        return rs.toString();
    }


    // devuelve si la ultima accion termino
    public boolean isAminDone()
    {
        return actionList.size()==0 || actionList.get(actionList.size() - 1).looping==mode.stopped;
    }

    public float frameNumber(int actionIndex)
    {
        float fn;
        ActionState as;
        Animation an;
        as=actionList.get(actionIndex);
        an=mesh.animaciones.get(as.actionId);

        if(as.looping==mode.stopped)
            fn=an.numFrames-1;  // ultimo frame
        else if(as.time==0)
            fn=0;               // primer frame
        else                    // ej: 3,5 es 3er frame a medio camino con el 4to
            fn=((an.numFrames - 1) / (an.timeLength / as.time));

        return fn;
    }

    // toma la pila de animaciones activas y las blendea con el peso correspondiente
    // matriz relativa al rest position.
    public void getAnimMatrix(float[] dest, boolean highDef)
    {
        float[] scratch=new float[16];
        float[] scratch2=new float[16];
        float[] scratch3=new float[16];

        Matrix.setIdentityM(scratch,0);

        for(int i=0; i<mesh.huesos.size(); i++)    // para cada hueso
        {
            for (int a=0; a<actionList.size(); a++)   // para cada accion de la lista apilada
            {
                float fn=frameNumber(a);
                if (a == 0)
                    mesh.interMatrix(scratch, 0, actionList.get(a).actionId, i, fn, highDef);
                else {
                    mesh.interMatrix(scratch2, 0, actionList.get(a).actionId, i, fn, highDef);
                    blendMatrix(scratch3, scratch, scratch2, 1 - actionList.get(a).weight);
                    System.arraycopy(scratch3, 0, scratch, 0, 16);
                }
            }
            // Copio la matriz resultado haciendola relativa al rest position
            Matrix.multiplyMM(dest, i*16, scratch,0,mesh.huesos.get(i).invRestMatrixFromRoot,0);
            //Matrix.multiplyMM(dest, i*16, mesh.huesos.get(i).matrizInv,0,scratch,0);
        }
    }

    // dadas dos matrices las interpola con un peso dado para la segunda
    // https://mycourses.aalto.fi/pluginfile.php/371079/mod_resource/content/1/05_quaternions.pdf
    private void blendMatrix(float[] dest, @NonNull float[] mat1, @NonNull float[] mat2, float weight)
    {
        float[] scratch = new float[16];
        float[] scratch2 = new float[16];
        float aSlerp;
        float v1Slerp;
        float v2Slerp;
        float v3Slerp;
        float xSlerp;
        float ySlerp;
        float zSlerp;

        float[] t1 = new float[3];
        float[] t2 = new float[3];
        t1[0]=mat1[12];t1[1]=mat1[13];t1[2]=mat1[14];
        t2[0]=mat2[12];t2[1]=mat1[13];t2[2]=mat1[14];

        Matrix.invertM(scratch,0,mat1,0);
        Matrix.multiplyMM(scratch2, 0, scratch, 0, mat2, 0);
        double angle=safeacos((scratch2[0]+scratch2[5]+scratch2[10]-1)/2); // Radians
        aSlerp = (float) (angle * 180 / (float) PI);  // Degrees

        // vector y angulo para interpolar ambas rotaciones
        if( Math.abs(angle)>0.0001 ) {
            double divisor = 2 * Math.sin(angle);
            v1Slerp = (float) ((scratch2[6] - scratch2[9]) / divisor);
            v2Slerp = (float) ((scratch2[8] - scratch2[2]) / divisor);
            v3Slerp = (float) ((scratch2[1] - scratch2[4]) / divisor);
        }
        else
        {
            v1Slerp=1;
            v2Slerp=v3Slerp=0;
        }
        // traslation
        xSlerp=scratch2[12];
        ySlerp=scratch2[13];
        zSlerp=scratch2[14];

        Matrix.setRotateM(scratch, 0, (aSlerp*weight), v1Slerp, v2Slerp, v3Slerp);
        scratch[12] = xSlerp*weight;
        scratch[13] = ySlerp*weight;
        scratch[14] = zSlerp*weight;
        Matrix.multiplyMM(dest, 0, mat1, 0, scratch, 0);
    }

    private void blendMatrix2(float[] dest, float[] m1, float[] m2, float weight)
    {
        float[] mat1 = new float[16];
        float[] mat2 = new float[16];

        for(int i=0; i<16; i++) {
            if(i==12 || i==13 || i==14) {
                mat1[i] = 0;
                mat2[i] = 0;
            }
            else {
                mat1[i] = m1[i];
                mat2[i] = m2[i];
            }
        }

        float[] scratch = new float[16];
        float[] scratch2 = new float[16];
        float aSlerp;
        float v1Slerp;
        float v2Slerp;
        float v3Slerp;

        Matrix.invertM(scratch,0,mat1,0);
        Matrix.multiplyMM(scratch2, 0, scratch, 0, mat2, 0);
        double angle=safeacos((scratch2[0]+scratch2[5]+scratch2[10]-1)/2); // Radians
        aSlerp = (float) (angle * 180 / (float) PI);  // Degrees

        // vector y angulo para interpolar ambas rotaciones
        if( Math.abs(angle)>0.0001 ) {
            double divisor = 2 * Math.sin(angle);
            v1Slerp = (float) ((scratch2[6] - scratch2[9]) / divisor);
            v2Slerp = (float) ((scratch2[8] - scratch2[2]) / divisor);
            v3Slerp = (float) ((scratch2[1] - scratch2[4]) / divisor);
        }
        else
        {
            v1Slerp=1;
            v2Slerp=v3Slerp=0;
        }

        Matrix.setRotateM(scratch, 0, (aSlerp*weight), v1Slerp, v2Slerp, v3Slerp);
        Matrix.multiplyMM(dest, 0, mat1, 0, scratch, 0);
        dest[12] = m1[12]+(m2[12]-m1[12])*weight;
        dest[13] = m1[13]+(m2[13]-m1[13])*weight;
        dest[14] = m1[14]+(m2[14]-m1[14])*weight;
    }

    // se asegura el rango de entrada para que no devuelve NaN
    private double safeacos(double x)
    {
        return Math.acos(Math.max(Math.min(x,1),-1));
    }
    public boolean isDrawable()   {return true;}
}