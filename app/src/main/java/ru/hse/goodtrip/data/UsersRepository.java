package ru.hse.goodtrip.data;

import android.net.Uri;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import retrofit2.Call;
import ru.hse.goodtrip.data.model.Result;
import ru.hse.goodtrip.data.model.ResultHolder;
import ru.hse.goodtrip.data.model.User;
import ru.hse.goodtrip.network.NetworkManager;
import ru.hse.goodtrip.network.authentication.LoginService;
import ru.hse.goodtrip.network.authentication.model.AuthenticationResponse;
import ru.hse.goodtrip.network.authentication.model.AuthorizationRequest;
import ru.hse.goodtrip.network.authentication.model.RegisterRequest;
import ru.hse.goodtrip.network.authentication.model.UrlHandler;

/**
 * Class that requests authentication and user information from the remote data source and maintains
 * an in-memory cache of login status and user credentials information.
 */
public class UsersRepository extends AbstractRepository {

  private static volatile UsersRepository instance;

  private final LoginService loginService;

  public User user = null;

  private UsersRepository() {
    super();
    this.loginService = NetworkManager.getInstance().getInstanceOfService(LoginService.class);

  }

  /**
   * Get instance of Login Repository.
   *
   * @return login repository.
   */
  public static UsersRepository getInstance() {
    if (instance == null) {
      instance = new UsersRepository();
    }
    return instance;
  }

  public static void changeUserMainPhoto(Uri newPhoto) {

  }

  public User getLoggedUser() {
    return user;
  }

  public boolean isLoggedIn() {
    return user != null;
  }

  public void logout() {
    user = null;
  }

  /**
   * set logged in user.
   *
   * @param user LoggedInUser user.
   */
  private void setLoggedInUser(User user) {
    this.user = user;
  }

  private void updatingToken(String username, String password) {
    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    executor.scheduleAtFixedRate(() -> {
      synchronized (this) {
        login(username, password);
      }
    }, 4, 4, TimeUnit.MINUTES);
    executor.shutdown();
  }

  /**
   * login user.
   *
   * @param username user name.
   * @param password password.
   * @return result value.
   */
  public CompletableFuture<Result<AuthenticationResponse>> login(String username, String password) {
    final ResultHolder<AuthenticationResponse> resultOfAuthorization = new ResultHolder<>();
    Call<AuthenticationResponse> loginServiceCall = loginService.login(
        new AuthorizationRequest(username, password));
    loginServiceCall.enqueue(
        getCallback(resultOfAuthorization, "Username or password are not correct"
            , (result) -> setLoggedInUser(
                new User(result.getId(), result.getHandle(),
                    result.getName() + " " + result.getSurname(),
                    result.getUrl(), result.getToken()))));
    updatingToken(username, password);
    return getCompletableFuture(resultOfAuthorization).whenCompleteAsync((result, throwable) -> {
      if (result.isSuccess()) {
        updatingToken(username, password);
      }
    });
  }

  public void updatePhoto(int userId, String uri, String token) {
    final ResultHolder<String> resultOfUpdatingPhoto = new ResultHolder<>();
    Call<String> loginServiceCall = loginService.updateUserPhoto(userId, new UrlHandler(uri),
        getWrappedToken(token));
    loginServiceCall.enqueue(
        getCallback(resultOfUpdatingPhoto, "Updating photo failed",
            (result) -> {
            }));
  }

  /**
   * Sign up user.
   *
   * @param username username.
   * @param password password.
   * @param handle   handle.
   * @param name     user name.
   * @param surname  user surname.
   * @return result value.
   */
  public CompletableFuture<Result<AuthenticationResponse>> signUp(String username, String password,
      String handle,
      String name,
      String surname) {
    final ResultHolder<AuthenticationResponse> resultOfAuthorization = new ResultHolder<>();
    Call<AuthenticationResponse> loginServiceCall = loginService.register(
        new RegisterRequest(username, handle, password, name, surname));
    loginServiceCall.enqueue(
        getCallback(resultOfAuthorization, "Username is not correct or is already taken"
            , authenticationResponse -> setLoggedInUser(
                new User(authenticationResponse.getId(), authenticationResponse.getHandle(),
                    authenticationResponse.getName() + " " + authenticationResponse.getSurname(),
                    authenticationResponse.getUrl(), authenticationResponse.getToken()))));
    return getCompletableFuture(resultOfAuthorization).whenCompleteAsync((result, throwable) -> {
      if (result.isSuccess()) {
        updatingToken(username, password);
      }
    });


  }
}