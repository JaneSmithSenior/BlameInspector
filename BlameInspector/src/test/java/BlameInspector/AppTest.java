import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.eclipse.egit.github.core.Issue;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.IssueService;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;

/**
 * Unit test for simple App.
 */
public class AppTest
        extends TestCase
{
    private String password = "";
    private String repoName = "BlameWhoTest";
    private String userName = "JackSmithJunior";
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public AppTest( String testName )
    {
        super( testName );
        System.setOut(new PrintStream(outContent));
        System.setProperty("user.dir", "C:\\Jack's Repo");
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( AppTest.class );
    }

    public void testSimpleTicket() throws IOException, GitAPIException, JSONException{
        ticketChecker("1","JaneSmithSenior");
    }

    public void testCorruptedTicket(){
//        Main.main(new String[] {repoName, "2", password});
//        assertTrue(outContent.toString().equals("Sorry, current ticket is corrupted!"));
    }

    public void testPackageTicket() throws JSONException, GitAPIException, IOException {
        ticketChecker("3", "JackSmithJunior");
    }
    public void testThirdLibraryException() throws JSONException, GitAPIException, IOException {
        ticketChecker("4", "JackSmithJunior");
    }

    protected void ticketChecker(String ticketNumber, String blameLogin) throws IOException, GitAPIException, JSONException
    {
        Main.main(new String[] {repoName, "1", password});
        GitHubClient client = new GitHubClient();
        client.setCredentials(userName, password);

        IssueService service = new IssueService(client);
        Issue issue = service.getIssue(userName, repoName, Integer.parseInt(ticketNumber));
        assertTrue(issue.getAssignee().getLogin().equals(blameLogin));

    }
}
