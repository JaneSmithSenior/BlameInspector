import com.jmolly.stacktraceparser.NFrame;
import com.jmolly.stacktraceparser.NStackTrace;
import com.jmolly.stacktraceparser.StackTraceParser;
import org.antlr.runtime.RecognitionException;
import org.eclipse.egit.github.core.Issue;
import org.eclipse.egit.github.core.User;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.IssueService;
import org.eclipse.jgit.api.BlameCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;


/**
 * Created by Alexander on 22.02.2016.
 */
public class Main {

    private static String user;
    private static String password;

    public static void main(String[] args) throws IOException, GitAPIException, JSONException {
        String repo = args[0];
        int num = Integer.parseInt(args[1]);
        user = getUserName();
        password = getUserPassword();
        GitHubClient client = new GitHubClient();
        client.setCredentials(user, password);

        IssueService service = new IssueService(client);
        Issue issue = service.getIssue(user, repo, num);
        String issueBody = issue.getBody();


        TraceInfo traceInfo = getTraceInfo(issueBody);
        Git git = Git.open(new File(System.getProperty("user.dir") + "/.git"));
//        Config config = git.getRepository().getConfig();
//        System.out.println(config.getString("user", null, "name"));

        BlameCommand cmd = new BlameCommand(git.getRepository());
        ObjectId commitID = git.getRepository().resolve("HEAD");
        RevCommit revCommit = new RevWalk(git.getRepository()).parseCommit(commitID);
        cmd.setStartCommit(commitID);
        cmd.setFilePath("src/" + traceInfo.getClassName() + ".java");
        BlameResult blameResult = cmd.call();

//        System.out.println(blameResult);
        String blamedUserEmail = blameResult.getSourceAuthor(traceInfo.getLineNumber()).getEmailAddress();
//        UserService userService = new UserService();
        String blamedUserLogin = getUserLogin(blamedUserEmail);
//        System.out.println(blamedUserEmail);
//        System.out.println(blamedUserLogin);

        User blamedUser = new User();
        issue.setAssignee(blamedUser.setLogin(blamedUserLogin));
        service.editIssue(user, repo, issue);
    }

    private static String getUserLogin(String blamedUserEmail) throws IOException, JSONException {

        String email = blamedUserEmail.split("@")[0];
        String url = "https://api.github.com/search/users?q=" + email + "+in:email";

        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();

        // optional default is GET
        con.setRequestMethod("GET");
        int responseCode = con.getResponseCode();

        BufferedReader in = new BufferedReader(
                new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        JSONObject searchReuslt = new JSONObject(response.toString().replaceAll(", ", ", \\"));
        JSONArray items = searchReuslt.getJSONArray("items");

        return items.getJSONObject(0).getString("login");
    }


    private static String readFromFile(String field){
        Path path = Paths.get("./config.txt");
        String result = "";
        try (BufferedReader reader = Files.newBufferedReader(path)){
            while (!result.startsWith(field)){
                result = reader.readLine();
            }
            result = result.substring(field.length() + 1);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    private static String getUserName(){
        return readFromFile("user");
    }

    private static String getUserPassword(){
        return readFromFile("password");
    }

    private static TraceInfo getTraceInfo(String issueBody){
        String levels [] = issueBody.split("\n");
        String lineNumber = "";
        for (int i = levels[1].length() - 3; i>=0; i--){
            if (levels[1].charAt(i)==':'){
                break;
            }
            lineNumber = levels[1].charAt(i) + lineNumber;
        }
        int lineNum = Integer.parseInt(lineNumber);
        NStackTrace stackTrace = null;
        try {
             stackTrace = StackTraceParser.parse(issueBody);
        }catch (RecognitionException e){
            System.out.println("Ticket corrupted!");
        }
        NFrame topFrame = stackTrace.getTrace().getFrames().get(0);
        System.out.println(topFrame.getLocation());
        return new TraceInfo(topFrame.getClassName(), topFrame.getMethodName(), lineNum);
    }

}
