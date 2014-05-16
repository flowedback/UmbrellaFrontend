package eu.eurofel.entities;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public class CountryNames {
	private Set<String> _countryNames = new TreeSet<String>();

	public CountryNames() {
		Locale[] _availableLocales = Locale.getAvailableLocales();

		for (Locale locale : _availableLocales) {
			_countryNames.add(locale.getDisplayCountry().toUpperCase());
		}
	}

	public Set<String> getSet() {
		return Collections.unmodifiableSet(_countryNames);
	}
}
