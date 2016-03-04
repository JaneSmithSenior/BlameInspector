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
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.NoSuchElementException;
import java.util.TreeMap;


/**
 * Created by Alexander on 22.02.2016.
 */
public class Main {

    private static TreeMap<String, String> filesInRepo;


    public static void main(String[] args) throws IOException, GitAPIException, JSONException {
        String repositoryName;
        int ticketNumber;
        String password;

        try {
            repositoryName = args[0];
            ticketNumber = Integer.parseInt(args[1]);
            password = args[2];
        }catch (ArrayIndexOutOfBoundsException e){
            System.out.println("Not enough arguments. Try again.");
            return;
        }

        filesInRepo = new TreeMap<>();

        Git git = Git.open(new File(System.getProperty("user.dir") + "/.git"));
        Config config = git.getRepository().getConfig();
        String userName = config.getString("user", null, "name");

        GitHubClient client = new GitHubClient();
        client.setCredentials(userName, password);

        IssueService service = new IssueService(client);
        Issue issue = service.getIssue(userName, repositoryName, ticketNumber);
        String issueBody = issue.getBody();

        BlameCommand cmd = new BlameCommand(git.getRepository());
        ObjectId commitID = git.getRepository().resolve("HEAD");

        RevWalk walk = new RevWalk(git.getRepository());
        RevCommit commit = walk.parseCommit(commitID);
        for (RevCommit com: walk){
            Date commitTime = new Date(com.getCommitTime() * 1000L);
            if (commitTime.before(issue.getUpdatedAt())){
                commit = com;
            }
        }
        RevTree tree = commit.getTree();

        TreeWalk treeWalk = new TreeWalk(git.getRepository());
        treeWalk.addTree(tree);
        treeWalk.setRecursive(true);
        while (treeWalk.next()) {
            filesInRepo.put(treeWalk.getNameString(), treeWalk.getPathString());
        }
        TraceInfo traceInfo;
        try {
            traceInfo  = getTraceInfo(issueBody);
        }catch (TicketCorruptedException e){
            System.out.println("Sorry, current ticket is corrupted!");
            return;
        }

        cmd.setStartCommit(commitID);
        cmd.setFilePath(filesInRepo.get(traceInfo.getFileName()));
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


    private static TraceInfo getTraceInfo(String issueBody) throws TicketCorruptedException {
        NStackTrace stackTrace;
        try {
             stackTrace = StackTraceParser.parse(issueBody);

        }catch (NoSuchElementException | RecognitionException e){
             throw new TicketCorruptedException("StackTrace corrupted!");
        }
        String locationInfo[];
        for (int i = 0 ;i < stackTrace.getTrace().getFrames().size(); i++){
            NFrame currentFrame = stackTrace.getTrace().getFrames().get(i);
            int size = currentFrame.getLocation().length();
            locationInfo = currentFrame.getLocation().substring(1,size -1).split(":");
            if (filesInRepo.containsKey(locationInfo[0])){
                return new TraceInfo(currentFrame.getClassName(), currentFrame.getMethodName(),
                        locationInfo[0], Integer.parseInt(locationInfo[1]));
            }
        }
        throw new TicketCorruptedException("No entry of exception found in current repository.");
    }

}
