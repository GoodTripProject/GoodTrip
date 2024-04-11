package ru.hse.goodtrip.ui.trips.plantrip;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import java.sql.Date;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import lombok.Getter;
import ru.hse.goodtrip.R;
import ru.hse.goodtrip.data.TripRepository;
import ru.hse.goodtrip.data.UsersRepository;
import ru.hse.goodtrip.data.model.ResultHolder;
import ru.hse.goodtrip.data.model.User;
import ru.hse.goodtrip.data.model.trips.City;
import ru.hse.goodtrip.data.model.trips.Coordinates;
import ru.hse.goodtrip.data.model.trips.Country;
import ru.hse.goodtrip.data.model.trips.CountryVisit;
import ru.hse.goodtrip.data.model.trips.ShowPlace;
import ru.hse.goodtrip.network.trips.model.AddTripRequest;
import ru.hse.goodtrip.network.trips.model.TripState;

public class PlanTripViewModel extends ViewModel {

  private final TripRepository tripRepository = TripRepository.getInstance();
  @Getter
  private final MutableLiveData<PlanTripFormState> planTripFormState = new MutableLiveData<>();
  List<CountryVisit> countries = new ArrayList<>();

  private Date parseDate(String dateString) throws DateTimeException {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    return new Date(
        LocalDate.parse(dateString, formatter).atStartOfDay().atZone(ZoneId.systemDefault())
            .toInstant().toEpochMilli());
  }

  /**
   * Creates trip and checks that trip values is correct.
   *
   * @param name                     name of trip.
   * @param startTripDate            departure date in string.
   * @param endTripDate              departure date in string.
   * @param mainPhotoUrl             photo of trip - optional.
   * @param moneyInUsd               budget of trip.
   * @param interestingPlacesToVisit places.
   */
  public void createTrip(String name, String startTripDate,
      String endTripDate, @Nullable String mainPhotoUrl, String moneyInUsd,
      Set<ShowPlace> interestingPlacesToVisit, User user) {
    if (isNameNotValid(name)) {
      planTripFormState.setValue(
          new PlanTripFormState(R.string.not_valid_name, null, null, null, null, null));
    } else if (isDateNotValid(startTripDate)) {
      planTripFormState.setValue(
          new PlanTripFormState(null, R.string.not_valid_date, null, null, null, null));
    } else if (isDateNotValid(endTripDate)) {
      planTripFormState.setValue(
          new PlanTripFormState(null, null, R.string.not_valid_date, null, null, null));
    } else if (parseDate(startTripDate).after(parseDate(endTripDate))) {
      planTripFormState.setValue(
          new PlanTripFormState(null, null, R.string.not_valid_date_order, null, null, null));
    } else if (isMoneyNotValid(moneyInUsd)) {
      planTripFormState.setValue(new PlanTripFormState(null, null, null, null, null, null));
    } else {
      planTripFormState.setValue(new PlanTripFormState(true));

      ResultHolder<String> result = tripRepository.addTrip(
          UsersRepository.getInstance().user.getId(), UsersRepository.getInstance().user.getToken(),
          new AddTripRequest(name, Integer.parseInt(moneyInUsd), mainPhotoUrl,
              parseDate(startTripDate), parseDate(endTripDate),
              TripState.PLANNED, Collections.emptyList(),
              countries.stream().map(TripRepository::getAddCountryRequestFromCountryVisit).collect(
                  Collectors.toList())));
      runExecutorToWaitResult(result, () -> {
      });
    }
  }

  private <T> void runExecutorToWaitResult(ResultHolder<T> result, Runnable func) {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Handler handler = new Handler(Looper.getMainLooper());
    executor.execute(() -> {
      synchronized (result) {
        try {
          result.wait();
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
      handler.post(func);
    });

  }

  private boolean isDateNotValid(String dateString) {
    // check date format is dd.mm.yyyy
    try {
      parseDate(dateString);
    } catch (DateTimeException e) {
      return true;
    }
    return false;
  }

  /**
   * Checker of correctness of budget.
   *
   * @param moneyInUsd string of budget of trip
   * @return true if money value is not correct, false otherwise
   */
  private boolean isMoneyNotValid(String moneyInUsd) {
    try {
      return Integer.parseInt(moneyInUsd) < 0;
    } catch (NumberFormatException nfe) {
      return true;
    }
  }

  /**
   * Countries from whole world.
   *
   * @return list of countries
   * @see <a
   * href="https://stackoverflow.com/questions/9760341/retrieve-a-list-of-countries-from-the-android-os"/>
   */
  public List<String> getCountriesList() {
    Locale[] locales = Locale.getAvailableLocales();
    ArrayList<String> countries = new ArrayList<>();
    for (Locale locale : locales) {
      String country = locale.getDisplayCountry();
      if (!country.trim().isEmpty() && !countries.contains(country)) {
        countries.add(country);
      }
    }
    Collections.sort(countries);
    return countries;
  }

  /**
   * Checker of correctness of name.
   *
   * @param name string of name of trip
   * @return true if name value is not correct, false otherwise
   */
  private boolean isNameNotValid(String name) {
    return !(name != null && name.trim().length() <= 32);
  }

  /**
   * add country to trip.
   *
   * @param countryName country name.
   * @param citiesName  name of cities.
   */
  public void addCountry(String countryName, List<String> citiesName) {
    Country country = new Country(countryName, new Coordinates(0, 0));
    List<City> cities = new ArrayList<>();
    for (String cityName : citiesName) {
      cities.add(new City(cityName, new Coordinates(0, 0), country));
    }
    CountryVisit countryVisit = new CountryVisit(country, cities);
    countries.add(countryVisit);
  }
}