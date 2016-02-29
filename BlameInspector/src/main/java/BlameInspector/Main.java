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
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;


/**
 * Created by Alexander on 22.02.2016.
 */
public class Main {


    public static void main(String[] args) throws IOException, GitAPIException, JSONException {
        String repositoryName = args[0];
        int ticketNumber = Integer.parseInt(args[1]);

        Git git = Git.open(new File(System.getProperty("user.dir") + "/.git"));
        Config config = git.getRepository().getConfig();
        String userName = config.getString("user", null, "name");
        System.out.println("Enter password: ");
        Scanner in = new Scanner(System.in);
        String password = in.nextLine();

        GitHubClient client = new GitHubClient();
        client.setCredentials(userName, password);

        IssueService service = new IssueService(client);
        Issue issue = service.getIssue(userName, repositoryName, ticketNumber);
        String issueBody = issue.getBody();

        TraceInfo traceInfo = getTraceInfo(issueBody);

        BlameCommand cmd = new BlameCommand(git.getRepository());
        ObjectId commitID = git.getRepository().resolve("HEAD");
        cmd.setStartCommit(commitID);
        cmd.setFilePath("src/" + traceInfo.getFileName());
        BlameResult blameResult = cmd.call();

        String blamedUserEmail = blameResult.getSourceAuthor(traceInfo.getLineNumber()).getEmailAddress();
        String blamedUserLogin = getUserLogin(blamedUserEmail);

        User blamedUser = new User();
        issue.setAssignee(blamedUser.setLogin(blamedUserLogin));
        service.editIssue(userName, repositoryName, issue);
    }

    private static String getUserLogin(String blamedUserEmail) throws IOException, JSONException {

        String email = blamedUserEmail.split("@")[0];
        String url = "https://api.github.com/search/users?q=" + email + "+in:email";

        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();
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


    private static TraceInfo getTraceInfo(String issueBody){
        NStackTrace stackTrace = null;
        try {
             stackTrace = StackTraceParser.parse(issueBody);
        }catch (RecognitionException e){
            System.out.println("Ticket corrupted!");
        }
        NFrame topFrame = stackTrace.getTrace().getFrames().get(0);
        int size = topFrame.getLocation().length();
        String locationInfo[] = topFrame.getLocation().substring(1,size -1).split(":");
        return new TraceInfo(topFrame.getClassName(), topFrame.getMethodName(),
                locationInfo[0], Integer.parseInt(locationInfo[1]));
    }

}
