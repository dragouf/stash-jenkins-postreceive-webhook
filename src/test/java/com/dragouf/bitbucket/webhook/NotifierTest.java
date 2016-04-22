package com.dragouf.bitbucket.webhook;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;

import com.atlassian.bitbucket.scm.http.HttpScmProtocol;
import com.atlassian.bitbucket.scm.ssh.SshScmProtocol;
import com.atlassian.bitbucket.user.EscalatedSecurityContext;
import com.dragouf.bitbucket.webhook.service.HttpClientFactory;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ClientConnectionManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.atlassian.bitbucket.hook.repository.RepositoryHook;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.setting.Settings;
import com.atlassian.bitbucket.permission.Permission;
import com.atlassian.bitbucket.user.SecurityService;
import com.atlassian.bitbucket.util.Operation;
import com.dragouf.bitbucket.webhook.service.SettingsService;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.*;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * Test for the Notifier class
 *
 * @author Peter Leibiger (kuhnroyal)
 * @author Michael Irwin (mikesir87)
 */
public class NotifierTest {

  private static final String JENKINS_BASE_URL = "http://localhost.jenkins";
  private static final String HTTP_CLONE_URL =
      "http://some.stash.com/scm/foo/bar.git";
  private static final String SSH_CLONE_URL =
      "ssh://git@some.stash.com:12345/foo/bar.git";
  private static final String CUSTOM_CLONE_URL =
      "http://custom.host/custom.git";

  private HttpClientFactory httpClientFactory;
  private HttpClient httpClient;
  private ClientConnectionManager connectionManager;
  private Repository repo;
  private RepositoryHook repoHook;
  private Settings settings;
  private SettingsService settingsService;
  private Notifier notifier;
  private SecurityService securityService;
  private SshScmProtocol sshScmProtocol;
  private HttpScmProtocol httpScmProtocol;

  /**
   * Setup tasks
   */
  @Before
  public void setup() throws Exception {
    httpClientFactory = mock(HttpClientFactory.class);
    settingsService = mock(SettingsService.class);
    securityService = mock(SecurityService.class);
      EscalatedSecurityContext escalatedSecurityContext = mock(EscalatedSecurityContext.class);

      // When the security service is called, run the underlying operation
    try {
        when(securityService.withPermission(any(Permission.class), any(String.class))).thenReturn(escalatedSecurityContext);
        when(escalatedSecurityContext.call(any(Operation.class)))
                .thenAnswer(new Answer<Object>() {
                    @Override
                    public Object answer(InvocationOnMock invocation) throws Throwable {
                        Object[] args = invocation.getArguments();
                        Operation<Object, Throwable> op
                                = (Operation<Object, Throwable>) args[0];

                        return op.perform();
                    }
                });
    } catch (Throwable t) {
        // Mock setup - can never happen
    }

    sshScmProtocol = mock(SshScmProtocol.class);
    httpScmProtocol = mock(HttpScmProtocol.class);
    notifier = new Notifier(settingsService, httpClientFactory, securityService, sshScmProtocol, httpScmProtocol);

    repo = mock(Repository.class);
    repoHook = mock(RepositoryHook.class);
    settings = mock(Settings.class);
    httpClient = mock(HttpClient.class);
    connectionManager = mock(ClientConnectionManager.class);

    when(repoHook.isEnabled()).thenReturn(true);
    when(settingsService.getRepositoryHook(repo)).thenReturn(repoHook);
    when(settingsService.getSettings(repo)).thenReturn(settings);
    when(httpClientFactory
        .getHttpClient(any(Boolean.class), any(Boolean.class)))
        .thenReturn(httpClient);
    when(httpClient.getConnectionManager()).thenReturn(connectionManager);

    when(httpScmProtocol.getCloneUrl(repo, null)).thenReturn(HTTP_CLONE_URL);

    when(sshScmProtocol.getCloneUrl(repo, null)).thenReturn(SSH_CLONE_URL);

    when(settings.getString(Notifier.JENKINS_BASE))
      .thenReturn(JENKINS_BASE_URL);
    when(settings.getString(Notifier.CLONE_TYPE)).thenReturn("http");
    when(settings.getBoolean(Notifier.IGNORE_CERTS, false)).thenReturn(false);
  }

  /**
   * Validates nothing happens if the hook isn't found
   * @throws Exception
   */
  @Test
  public void shouldReturnEarlyWhenHookIsNull() throws Exception {
    when(settingsService.getRepositoryHook(repo)).thenReturn(null);
    notifier.notify(repo, "refs/heads/master", "sha1");
    verify(httpClientFactory, never())
      .getHttpClient(anyBoolean(), anyBoolean());
  }

  /**
   * Validates that nothing happens if the hook is disabled
   * @throws Exception
   */
  @Test
  public void shouldReturnEarlyWhenHookIsNotEnabled() throws Exception {
    when(repoHook.isEnabled()).thenReturn(false);
    notifier.notify(repo, "refs/heads/master", "sha1");
    verify(httpClientFactory, never())
        .getHttpClient(anyBoolean(), anyBoolean());
  }

  /**
   * Validates that nothing happens if the settings aren't set properly
   * @throws Exception
   */
  @Test
  public void shouldReturnEarlyWhenSettingsAreNull() throws Exception {
    when(settingsService.getSettings(repo)).thenReturn(null);
    notifier.notify(repo, "refs/heads/master", "sha1");
    verify(httpClientFactory, never())
      .getHttpClient(anyBoolean(), anyBoolean());
  }

  /**
   * Validates that the URL is correct when using an HTTP clone type
   * @throws Exception
   */
  @Test
  public void shouldCallTheCorrectUrlWithHttpCloneType() throws Exception {
    when(settings.getString(Notifier.CLONE_TYPE)).thenReturn("http");
    notifier.notify(repo, "refs/heads/master", "sha1");

    ArgumentCaptor<HttpGet> captor = ArgumentCaptor.forClass(HttpGet.class);

    verify(httpClientFactory, times(1)).getHttpClient(false, false);
    verify(httpClient, times(1)).execute(captor.capture());
    verify(connectionManager, times(1)).shutdown();

    assertEquals("http://localhost.jenkins/git/notifyCommit?"
        + "url=http%3A%2F%2Fsome.stash.com%2Fscm%2Ffoo%2Fbar.git"
        + "&branches=refs%2Fheads%2Fmaster"
        + "&sha1=sha1",
        captor.getValue().getURI().toString());
  }

  /**
   * Validates that the URL is correct when using an SSH clone type
   * @throws Exception
   */
  @Test
  public void shouldCallTheCorrectUrlWithSshCloneType() throws Exception {
    when(settings.getString(Notifier.CLONE_TYPE)).thenReturn("ssh");
    notifier.notify(repo, "refs/heads/master", "sha1");

    ArgumentCaptor<HttpGet> captor = ArgumentCaptor.forClass(HttpGet.class);

    verify(httpClientFactory, times(1)).getHttpClient(false, false);
    verify(httpClient, times(1)).execute(captor.capture());
    verify(connectionManager, times(1)).shutdown();

    assertEquals("http://localhost.jenkins/git/notifyCommit?"
        + "url=ssh%3A%2F%2Fgit%40some.stash.com%3A12345%2Ffoo%2Fbar.git"
        + "&branches=refs%2Fheads%2Fmaster"
        + "&sha1=sha1",
        captor.getValue().getURI().toString());
  }

  /**
   * Validates that the URL is correct when using a custom clone type
   * @throws Exception
   */
  @Test
  public void shouldCallTheCorrectUrlWithCustomCloneType() throws Exception {
    when(settings.getString(Notifier.CLONE_TYPE)).thenReturn("custom");
    when(settings.getString(Notifier.CLONE_URL)).thenReturn(CUSTOM_CLONE_URL);

    notifier.notify(repo, "refs/heads/master", "sha1");

    ArgumentCaptor<HttpGet> captor = ArgumentCaptor.forClass(HttpGet.class);

    verify(httpClientFactory, times(1)).getHttpClient(false, false);
    verify(httpClient, times(1)).execute(captor.capture());
    verify(connectionManager, times(1)).shutdown();

    assertEquals("http://localhost.jenkins/git/notifyCommit?"
        + "url=http%3A%2F%2Fcustom.host%2Fcustom.git"
        + "&branches=refs%2Fheads%2Fmaster"
        + "&sha1=sha1",
        captor.getValue().getURI().toString());
  }

  /**
   * Validates that the URL is correct when using a null clone type
   * @throws Exception
   */
  @Test
  public void shouldCallTheCorrectUrlWithNullCloneType() throws Exception {
    when(settings.getString(Notifier.CLONE_TYPE)).thenReturn(null);
    when(settings.getString(Notifier.CLONE_URL)).thenReturn(CUSTOM_CLONE_URL);

    notifier.notify(repo, "refs/heads/master", "sha1");

    ArgumentCaptor<HttpGet> captor = ArgumentCaptor.forClass(HttpGet.class);

    verify(httpClientFactory, times(1)).getHttpClient(false, false);
    verify(httpClient, times(1)).execute(captor.capture());
    verify(connectionManager, times(1)).shutdown();

    assertEquals("http://localhost.jenkins/git/notifyCommit?"
        + "url=http%3A%2F%2Fcustom.host%2Fcustom.git"
        + "&branches=refs%2Fheads%2Fmaster"
        + "&sha1=sha1",
        captor.getValue().getURI().toString());
  }

  /**
   * Validates that the URL is correct when using a null clone type
   * @throws Exception
   */
  @Test
  public void shouldFailWithInvalidCloneType() throws Exception {
    when(settings.getString(Notifier.CLONE_TYPE)).thenReturn("invalid");

    NotificationResult res = notifier.notify(repo, "refs/heads/master", "sha1");
    assertFalse(res.isSuccessful());
  }

  /**
   * Validates the URL is correct when using a non-SSL path
   * @throws Exception
   */
  @Test
  public void shouldCallTheCorrectUrlWithoutSsl() throws Exception {
    notifier.notify(repo, "refs/heads/master", "sha1");

    ArgumentCaptor<HttpGet> captor = ArgumentCaptor.forClass(HttpGet.class);

    verify(httpClientFactory, times(1)).getHttpClient(false, false);
    verify(httpClient, times(1)).execute(captor.capture());
    verify(connectionManager, times(1)).shutdown();

    assertEquals("http://localhost.jenkins/git/notifyCommit?"
        + "url=http%3A%2F%2Fsome.stash.com%2Fscm%2Ffoo%2Fbar.git"
        + "&branches=refs%2Fheads%2Fmaster"
        + "&sha1=sha1",
        captor.getValue().getURI().toString());
  }

  /**
   * Validates the path is correct when using a SSL path
   * @throws Exception
   */
  @Test
  public void shouldCallTheCorrectUrlWithSsl() throws Exception {
    when(settings.getString(Notifier.JENKINS_BASE))
      .thenReturn(JENKINS_BASE_URL.replace("http", "https"));

    notifier.notify(repo, "refs/heads/master", "sha1");

    ArgumentCaptor<HttpGet> captor = ArgumentCaptor.forClass(HttpGet.class);

    verify(httpClientFactory, times(1)).getHttpClient(true, false);
    verify(httpClient, times(1)).execute(captor.capture());
    verify(connectionManager, times(1)).shutdown();

    assertEquals("https://localhost.jenkins/git/notifyCommit?"
        + "url=http%3A%2F%2Fsome.stash.com%2Fscm%2Ffoo%2Fbar.git"
        + "&branches=refs%2Fheads%2Fmaster"
        + "&sha1=sha1",
        captor.getValue().getURI().toString());
  }

  /**
   * Validates that the correct path is taken when using SSL but ignoring cert
   * validation.
   * @throws Exception
   */
  @Test
  public void shouldCallTheCorrectUrlWithSslAndIgnoreCerts() throws Exception {
    when(settings.getString(Notifier.JENKINS_BASE))
      .thenReturn(JENKINS_BASE_URL.replace("http", "https"));
    when(settings.getBoolean(Notifier.IGNORE_CERTS, false)).thenReturn(true);

    notifier.notify(repo, "refs/heads/master", "sha1");

    ArgumentCaptor<HttpGet> captor = ArgumentCaptor.forClass(HttpGet.class);

    verify(httpClientFactory, times(1)).getHttpClient(true, true);
    verify(httpClient, times(1)).execute(captor.capture());
    verify(connectionManager, times(1)).shutdown();

    assertEquals("https://localhost.jenkins/git/notifyCommit?"
        + "url=http%3A%2F%2Fsome.stash.com%2Fscm%2Ffoo%2Fbar.git"
        + "&branches=refs%2Fheads%2Fmaster"
        + "&sha1=sha1",
        captor.getValue().getURI().toString());
  }

  /**
   * Validates that the correct path is used, even when a trailing slash
   * is provided on the Jenkins Base URL
   * @throws Exception
   */
  @Test
  public void shouldCallTheCorrectUrlWithTrailingSlashOnJenkinsBaseUrl()
      throws Exception {
    when(settings.getString(Notifier.JENKINS_BASE))
      .thenReturn(JENKINS_BASE_URL.concat("/"));

    notifier.notify(repo, "refs/heads/master", "sha1");

    ArgumentCaptor<HttpGet> captor = ArgumentCaptor.forClass(HttpGet.class);

    verify(httpClientFactory, times(1)).getHttpClient(false, false);
    verify(httpClient, times(1)).execute(captor.capture());
    verify(connectionManager, times(1)).shutdown();

    assertEquals("http://localhost.jenkins/git/notifyCommit?"
        + "url=http%3A%2F%2Fsome.stash.com%2Fscm%2Ffoo%2Fbar.git"
        + "&branches=refs%2Fheads%2Fmaster"
        + "&sha1=sha1",
        captor.getValue().getURI().toString());
  }

  /**
   * Validates that the correct path is used when the omitBranchName option
   * is on
   * @throws Exception
   */
  @Test
  public void shouldCallTheCorrectURLWithOmitBranchNameOn()
    throws Exception {
    when(settings.getBoolean(Notifier.OMIT_BRANCH_NAME, false)).thenReturn(true);
    notifier.notify(repo, "refs/heads/master", "sha1");

    ArgumentCaptor<HttpGet> captor = ArgumentCaptor.forClass(HttpGet.class);

    verify(httpClientFactory, times(1)).getHttpClient(false, false);
    verify(httpClient, times(1)).execute(captor.capture());
    verify(connectionManager, times(1)).shutdown();

    assertEquals("http://localhost.jenkins/git/notifyCommit?"
        + "url=http%3A%2F%2Fsome.stash.com%2Fscm%2Ffoo%2Fbar.git"
        + "&sha1=sha1",
        captor.getValue().getURI().toString());
  }

  /**
   * Validates that the correct path is used when the omitBranchName option
   * is off
   * @throws Exception
   */
  @Test
  public void shouldCallTheCorrectURLWithOmitBranchNameOff()
    throws Exception {
    when(settings.getBoolean(Notifier.OMIT_BRANCH_NAME, false)).thenReturn(false);
    notifier.notify(repo, "refs/heads/master", "sha1");

    ArgumentCaptor<HttpGet> captor = ArgumentCaptor.forClass(HttpGet.class);

   verify(httpClientFactory, times(1)).getHttpClient(false, false);
   verify(httpClient, times(1)).execute(captor.capture());
   verify(connectionManager, times(1)).shutdown();

   assertEquals("http://localhost.jenkins/git/notifyCommit?"
       + "url=http%3A%2F%2Fsome.stash.com%2Fscm%2Ffoo%2Fbar.git"
       + "&branches=refs%2Fheads%2Fmaster"
       + "&sha1=sha1",
       captor.getValue().getURI().toString());
  }

  /**
   * Validates that the correct path is used when the sha1 is null
   * @throws Exception
   */
  @Test
  public void shouldCallTheCorrectURLWhenSha1IsNull()
    throws Exception {
    when(settings.getBoolean(Notifier.OMIT_BRANCH_NAME, false)).thenReturn(false);
    notifier.notify(repo, "refs/heads/master", null);

    ArgumentCaptor<HttpGet> captor = ArgumentCaptor.forClass(HttpGet.class);

    verify(httpClientFactory, times(1)).getHttpClient(false, false);
    verify(httpClient, times(1)).execute(captor.capture());
    verify(connectionManager, times(1)).shutdown();

    assertEquals("http://localhost.jenkins/git/notifyCommit?"
        + "url=http%3A%2F%2Fsome.stash.com%2Fscm%2Ffoo%2Fbar.git"
        + "&branches=refs%2Fheads%2Fmaster",
        captor.getValue().getURI().toString());
  }
}
