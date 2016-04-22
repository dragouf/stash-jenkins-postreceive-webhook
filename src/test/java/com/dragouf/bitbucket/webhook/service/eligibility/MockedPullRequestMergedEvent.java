package com.dragouf.bitbucket.webhook.service.eligibility;

import com.atlassian.bitbucket.event.pull.PullRequestMergedEvent;
import com.atlassian.bitbucket.pull.PullRequest;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.user.ApplicationUser;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;

import static org.mockito.Mockito.mock;

/**
 * A PullRequestMergedEvent used for testing.
 *
 * Had to mock because the getUser is final and cannot be stubbed. This sets the
 * user field through reflection, allowing the getUser to still work.
 *
 * @author Michael Irwin (mikesir87)
 */
public class MockedPullRequestMergedEvent extends PullRequestMergedEvent {

  private static final long serialVersionUID = 6015228907835452814L;
  private Repository repository;

  /**
   * Creates a new instance
   */
  public MockedPullRequestMergedEvent() {
    super("TEST", mock(PullRequest.class));
  }

  /**
   * Set the StashUser for the event, using reflection.
   * @param repository The repository for the event
   */
  public void setRepository(Repository repository) throws Exception {
    this.repository = repository;
  }

  @Override
  @Nonnull
  public Repository getRepository() {
    return repository;
  }

  /**
   * Set the StashUser for the event, using reflection.
   * @param user The user for the event
   */
  public void setUser(ApplicationUser user) throws Exception {
    Field field = ApplicationUser.class.getDeclaredField("user");
    field.setAccessible(true);
    field.set(this, user);
  }

}
