/**
 * Created by Alexander on 26.02.2016.
 */
public class TraceInfo {
    private String className;
    private String methodName;
    private int lineNumber;

    public TraceInfo(String className, String methodName, int lineNumber){
        this.className = className;
        this.methodName = methodName;
        this.lineNumber = lineNumber;
    }

    public int getLineNumber(){
        return lineNumber;
    }

    public String getClassName(){
        return className;
    }

    public String getMethodName(){
        return methodName;
    }
}
