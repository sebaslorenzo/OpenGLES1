package com.example.admin.opengles1;

import android.opengl.Matrix;
import android.util.Log;

import java.util.ArrayList;

public class Robot extends Puppet {
    public robotState estadoRob=robotState.inicial;
    public float[] destino={0,0,0,1};
    public ArrayList<String> idleAction=new ArrayList<>();
    prg todo;
    // if exp <comp> exp { }
    // let var=exp
    // add var,exp
    // goto exp,exp
    // gotornd x1,y1,x2,y2
    // wait action1[,actionN]       hasta 4 acciones
    // print expr
    //getmsg type
    // expr := -0.4 variable time() dist() rnd() actionend()

    final String script =
            "if estado=0 { let wt=time();add wt,5;wait parado;add estado,1;}"+
            "if estado=1 { if time()>wt {rndgoto -21,-14,-3,0; add estado,1}}"+ // 8,20
            "if estado=2 { if dist()<0.5 {let wt=time();add wt,1;wait parado,Mirar,Tirar;add estado,-1;}}"+
            "if estado=3 { if time()>wt {goto -3,2; add estado,1}}"+
            "if estado=4 { if dist()<0.5 {let wt=time();add wt,5;wait Tirar,Mirar;add estado,1;}}"+
            "if estado=5 { if time()>wt {goto -6,17; add estado,1}}"+
            "if estado=6 { if dist()<0.5 {let wt=time();add wt,5;wait Mirar;set estado=1;}}";

    enum robotState {inicial, espera, yendo}

    public Robot(String meshName)
    {
        super(meshName);
        todo=new prg(script, this);
    }

    // ahora es seguro hacer la destruccion?
    public void destroy()
    {
        super.destroy();
    }

    //public void makeItAlive(boolean mover, float angulo, boolean saltar, float dt)
    public void makeItAlive(float dt)
    {
        todo.run();
        Log.d("MyApp", "Robot: "+estadoRob);

        if(estadoRob==robotState.espera)
                super.makeItAlive(false,0.0f, false, dt);
        if(estadoRob==robotState.yendo)
        {
            float[] mat= getWorldMatrixClone();   // matriz global del personaje
            float[] scratch=new float[16];
            float dist=Math.abs(mat[12]-destino[0])+Math.abs(mat[14]-destino[2]);

            float[] v1=new float[4];
            double ang;
            Matrix.invertM(scratch,0,mat,0);
            Matrix.multiplyMV(v1,0,scratch,0,destino,0);
            if(v1[2]<0 || Math.abs(v1[0])>v1[2])
                ang=Math.signum(v1[0]);
            else
                ang=Math.atan(v1[0]/v1[2]);
            Log.d("MyApp", "Robot yendo: D="+dist+" A="+ang+" x="+v1[0]+" z="+v1[2]);
            if( Math.abs(ang)>1 )
                ang=Math.signum(ang);

            super.makeItAlive(true, (float)ang, false, dt);
        }
    }

    public String getIdleAnimName()
    {
        lookAtMe=false;
        if(idleAction.size()==0)
            return "";
        return idleAction.get((int)Math.floor(0.99*Math.random()*idleAction.size()));
    }
}

class prg{
    int ubi;
    String code;
    ArrayList<sentence> commands;
    ArrayList<String> vars;
    float[] varVals;
    Robot commanded;
    long start;

    public prg(String the_code, Robot rob)
    {
        code=the_code;
        commanded=rob;
        commands=new ArrayList<>();
        vars=new ArrayList<>();
        ubi=0;
        start=System.currentTimeMillis();

        compile();
        varVals=new float[vars.size()];
        for(int i=0; i<vars.size();i++)
            varVals[i]=0;
    }

    public void run()
    {
        int i=0;
        while(i<commands.size())
        {
            sentence sent=commands.get(i);
            if(sent.verb==token.let)
                varVals[sent.val[0].index]=getSentenceValue(sent,1);
            else if(sent.verb==token.add)
                varVals[sent.val[0].index]+=getSentenceValue(sent,1);
            else if(sent.verb==token.when)
                if(!eval(sent.val[1].tokenValue,getSentenceValue(sent, 0), getSentenceValue(sent, 2)))
                    i = sent.next - 1;
            if(sent.verb==token.gotoxy) {
                commanded.destino[0]=getSentenceValue(sent, 0);
                commanded.destino[2]=getSentenceValue(sent, 1);
                Log.d("MyApp", "Code: goto "+getSentenceValue(sent, 0)+","+getSentenceValue(sent, 1));
                commanded.estadoRob= Robot.robotState.yendo;
            }
            if(sent.verb==token.rndgoto) {
                float l1=getSentenceValue(sent, 0);
                float l2=getSentenceValue(sent, 2);
                float xy=(float)Math.random()*(l2-l1)+l1;
                commanded.destino[0]=xy;

                l1=getSentenceValue(sent, 1);
                l2=getSentenceValue(sent, 3);
                xy=(float)Math.random()*(l2-l1)+l1;
                commanded.destino[2]=xy;

                Log.d("MyApp", "Code: rndgoto "+commanded.destino[0]+","+commanded.destino[2]);
                commanded.estadoRob= Robot.robotState.yendo;
            }
            if(sent.verb==token.wait) {
                commanded.idleAction.clear();
                for(int j=0; j<sent.next;j++)
                    commanded.idleAction.add(sent.val[j].literal);
                commanded.estadoRob= Robot.robotState.espera;
            }
            if(sent.verb==token.print) {
                Log.d("MyApp", "Code: print "+sent.val[0].asText()+"="+getSentenceValue(sent, 0));
            }

            i++;
        }
    }

    boolean eval(token cond, float f1, float f2)
    {
        if( cond==token.eq )
            return f1==f2;
        if( cond==token.noteq )
            return f1!=f2;
        if( cond==token.gt )
            return f1>f2;
        if( cond==token.gte )
            return f1>=f2;
        if( cond==token.less )
            return f1<f2;
        if( cond==token.lesseq )
            return f1<=f2;
        return false;
    }

    float getSentenceValue(sentence sen, int parm)
    {
        if(sen.val[parm].contentType==valueType.ind)
            return varVals[sen.val[parm].index];
        if(sen.val[parm].contentType==valueType.num)
            return sen.val[parm].number;
        if(sen.val[parm].contentType==valueType.token)
        {
            if(sen.val[parm].tokenValue==token.distance)
            {
                float[] mat=commanded.getWorldMatrixClone();   // matriz global del personaje
                return Math.abs(mat[12]-commanded.destino[0])+Math.abs(mat[14]-commanded.destino[2]); // cambiar al destino que se usará
            }
            if(sen.val[parm].tokenValue==token.time)
                return (System.currentTimeMillis()-start) / 1000.0f;

            if(sen.val[parm].tokenValue==token.actionend)
                return commanded.isAminDone()?1:0;

            if(sen.val[parm].tokenValue==token.rnd)
                return (float)Math.random();
        }
        return 0;
    }

    boolean compile()
    {
        boolean error=false;

        while(ubi<code.length() && !error) {
            if( follows(";") ) {
                Log.d("MyApp", "Code: ;");
            }
            else if( follows("wait") )
            {
                sentence sen=new sentence(token.wait);
                commands.add(sen);
                if(getLiteral(sen, false)) {
                    StringBuilder actions=new StringBuilder();
                    int i = 1;
                    while(!error && i<4 && follows(","))
                    {
                        if(getLiteral(sen, false))
                        {
                            actions.append(",");
                            actions.append(sen.val[i].literal);
                        }
                        else
                            error=true;
                        i++;
                    }
                    if(!error && getEol()) {
                        sen.next=i; // guardo aca cuantas variables hay dirty
                        Log.d("MyApp", "Code: wait(" + sen.val[0].asText() + actions + ")");
                    }
                }
                else
                    error=true;
            }
            else if( follows("goto") )
            {
                sentence sen=new sentence(token.gotoxy);
                commands.add(sen);
                if(getNumLit(sen) && follows(",") && getNumLit(sen) & getEol())
                    Log.d("MyApp", "Code: goto(" + sen.val[0].asText() + "," + sen.val[1].asText() + ")");
                else
                    error = true;
            }
            else if( follows("rndgoto") )
            {
                sentence sen=new sentence(token.rndgoto);
                commands.add(sen);
                if(getNumLit(sen) && follows(",") && getNumLit(sen) && follows(",") && getNumLit(sen) && follows(",") && getNumLit(sen) & getEol())
                    Log.d("MyApp", "Code: rndgoto(" + sen.val[0].asText() + "," + sen.val[1].asText() + "," + sen.val[2].asText() + "," + sen.val[3].asText() + ")");
                else
                    error = true;
            }
            else if( follows("if") )
            {
                sentence sen=new sentence(token.when);
                commands.add(sen);
                if(getNumLit(sen) && getComp(sen) && getNumLit(sen) && follows("{") )
                {
                    Log.d("MyApp", "Code: if("+sen.val[0].asText()+" "+sen.val[1].asText()+" "+sen.val[2].asText()+") "+" {");
                    int remember= commands.size();

                    if (compile() && follows("}")) {
                        Log.d("MyApp", "Code: }");
                        commands.get(remember-1).next=commands.size();
                    }
                    else
                        error = true;
                }
                else
                    error = true;
            }
            if( follows("let") ||  follows("set") ) {
                sentence sen=new sentence(token.let);
                commands.add(sen);
                if(getLiteral(sen,true) && (follows(",")|| follows("=")) && getNumLit(sen) && getEol())
                    Log.d("MyApp", "Code: let(" + sen.val[0].asText() + "," + sen.val[1].asText() + ")");
                else
                    error = true;
            }
            if( follows("add") ) {
                sentence sen=new sentence(token.add);
                commands.add(sen);
                if(getLiteral(sen,true) && (follows(",")|| follows("=")) && getNumLit(sen) && getEol())
                    Log.d("MyApp", "Code: add(" + sen.val[0].asText() + "," + sen.val[1].asText() + ")");
                else
                    error = true;
            }
            if( follows("print") ) {
                sentence sen=new sentence(token.print);
                commands.add(sen);
                if(getNumLit(sen) && getEol())
                    Log.d("MyApp", "Code: print(" + sen.val[0].asText() + ")");
                else
                    error = true;
            }
            if( follows("readmsg") ) {
                sentence sen=new sentence(token.readmsg);
                commands.add(sen);
                if(getLiteral(sen,false) && getEol())
                    Log.d("MyApp", "Code: readmsg(" + sen.val[0].asText() + ")");
                else
                    error = true;
            }
            else if( follows("}") )
            {
                ubi--;
                return true;
            }
        }
        return !error;
    }

    // si no esta ya la agrega
    int addVar(String s)
    {
        int v=getVar(s);
        if(v!=-1)
            return v;
        vars.add(s);
        return vars.size()-1;
    }

    // si no esta ya la agrega
    int getVar(String s)
    {
        for(int i=0;i<vars.size();i++)
            if(vars.get(i).equals(s))
                return i;

        return -1;
    }

    void eat_space()
    {
        while(ubi<code.length() && code.charAt(ubi)==' ')
            ubi++;
    }

    boolean follows(String palabra)
    {
        eat_space();
        if(code.length()-ubi-palabra.length()<0)
            return false;
        for(int i=0;i<palabra.length();i++) {
            if (code.charAt(ubi + i) != palabra.charAt(i))
                return false;
        }
        ubi+=palabra.length();
        return true;
    }

    // solo toma literales texto
    boolean getLiteral(sentence ns, boolean saveVar)
    {
        eat_space();
        StringBuilder text= new StringBuilder();
        while(ubi<code.length() && isAlphabetic())
            text.append(code.charAt(ubi++));

        if(text.length()>0) {

            if(saveVar) {
                ns.addInd(addVar(text.toString()), text.toString());
            }
            else
                ns.addLiteral(text.toString());
            return true;
        }
        return false;
    }

    // solo tome comparaciones
    boolean getComp(sentence ns)
    {
        token valor;
        eat_space();
        if(follows("<>"))
            valor=token.noteq;
        else if(follows("<="))
            valor=token.lesseq;
        else if(follows("<"))
            valor=token.less;
        else if(follows(">="))
            valor=token.gte;
        else if(follows(">"))
            valor=token.gt;
        else if(follows("="))
            valor=token.eq;
        else
            return false;

        ns.addToken(valor);
        return true;
    }

    // solo toma numeros
    boolean getNumber(sentence ns)
    {
        int signo=1;
        float dest;
        float decimal=0;
        boolean found=false;

        eat_space();
        dest=0;
        if(follows("-"))
            signo=-1;

        while(ubi<code.length() && isNumeric())
        {
            found=true;
            dest=dest*10+code.charAt(ubi++)-'0';
        }

        if(follows("."))
        {
            float divisor=10;
            while(ubi<code.length() && isNumeric()) {
                decimal += (code.charAt(ubi++) - '0') / divisor;
                divisor = divisor * 10;
            }
        }
        if(found)
            ns.addNumber(signo*(dest+decimal));

        return found;
    }

    // toma numeros o variables (literales)
    boolean getNumLit(sentence ns)
    {
        if( getNumber(ns) )
            return true;

        if(follows("dist()")) {
            ns.addToken(token.distance);
            return true;
        }
        if(follows("time()")) {
            ns.addToken(token.time);
            return true;
        }
        if(follows("actionend()")) {
            ns.addToken(token.actionend);
            return true;
        }
        if(follows("rnd()")) {
            ns.addToken(token.rnd);
            return true;
        }
        return getLiteral(ns, true);
    }

    boolean getEol()
    {
        eat_space();
        if( follows(";") || ubi>=code.length() )
            return true;

        if( follows("}") ) {
            ubi--;
            return true;
        }
        return false;
    }

    boolean isAlphabetic()
    {
        if(code.charAt(ubi)>='a' && code.charAt(ubi)<='z')
            return true;
        if(code.charAt(ubi)>='A' && code.charAt(ubi)<='Z')
            return true;

        return code.charAt(ubi) == '_';
    }

    boolean isNumeric()
    {
        return code.charAt(ubi) >= '0' && code.charAt(ubi) <= '9';
    }

}

enum valueType {lit,num,token,empty,ind}

enum token {let, add, when, readmsg, wait, gotoxy, rndgoto, print, gt, gte, less, lesseq, eq, noteq,time, distance, actionend, rnd}

class value{
    valueType contentType;
    String literal;
    float number;
    token tokenValue;
    int index;
    public value(String lit) {setValue(lit);}
    public value(float f) {setValue(f);}
    public value(token t) {setValue(t);}
    public value(int i, String s) {setValue(i);literal= s;}

    public void setValue(String lit) {literal=lit;contentType=valueType.lit;}
    public void setValue(float f) {number=f;contentType=valueType.num;}
    public void setValue(token t) {tokenValue=t;contentType=valueType.token;}
    public void setValue(int i) {index=i;contentType=valueType.ind;}

    public value() {contentType=valueType.empty;}
    public valueType getType() {return contentType;}
    public String asText()
    {
        if(contentType==valueType.lit)
            return literal;
        if(contentType==valueType.num)
            return String.valueOf(number);
        if(contentType==valueType.ind)
            return literal+"("+ index +")";
        if(contentType==valueType.token)
            return String.valueOf(tokenValue);
        return "<null>";
    }
    public value clone()
    {
        value v=new value();
        v.copy(this);
        return v;
    }

    public void copy(value v)
    {
        contentType=v.contentType;
        if(v.contentType==valueType.lit)
            literal= v.literal;
        else if(v.contentType==valueType.num)
            number=v.number;
        else if(v.contentType==valueType.ind)
            index=v.index;
        else if(v.contentType==valueType.token)
            tokenValue=v.tokenValue;
    }
}

class sentence {
    token verb;
    public int next;
    public value[] val;
    int count=0;

    public sentence(token v) {verb=v; val=new value[4];next=0;}

    public void addLiteral(String s) {val[count++]=new value(s);}
    public void addNumber(float f) {val[count++]=new value(f);}
    public void addInd(int i, String s) {val[count++]=new value(i, s);}
    public void addToken(token t) {val[count++]=new value(t);}
}