package eu.eurofel.services.impl;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.UUID;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.ModificationItem;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import org.apache.commons.io.FileUtils;

import eu.eurofel.Messages;
import eu.eurofel.entities.BridgeFederation;
import eu.eurofel.entities.EAAAccount;
import eu.eurofel.entities.Notification;
import eu.eurofel.services.DisseminationService;
import eu.eurofel.services.EAAService;
import eu.eurofel.services.NotificationService;
import eu.eurofel.util.Constants;
import eu.eurofel.util.EAAHash;
import eu.eurofel.util.RetrieveNavigation;

public class EAAServiceImpl implements EAAService {

	private ResourceBundle eurofel = ResourceBundle.getBundle("eaa");

	DirContext ctx;

	private DisseminationService disseminationService;

	private NotificationService notificationService;

	public EAAServiceImpl() {
		Hashtable<String, String> env = new Hashtable<String, String>();
		env.put(Context.INITIAL_CONTEXT_FACTORY,
				eurofel.getString("eaa.initial_context_factory"));
		env.put(Context.PROVIDER_URL, eurofel.getString("eaa.provider_url"));
		env.put(Context.SECURITY_AUTHENTICATION,
				eurofel.getString("eaa.security_authentication"));
		env.put(Context.SECURITY_PRINCIPAL,
				eurofel.getString("eaa.security_principal")); // specify
																// the
																// username
		env.put(Context.SECURITY_CREDENTIALS,
				eurofel.getString("eaa.security_credentials"));

		try {
			ctx = new InitialDirContext(env);
		} catch (NamingException e) {
			e.printStackTrace();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * eu.eurofel.services.EAAService#createAccount(eu.eurofel.entities.EAAAccount
	 * )
	 */

	public void createAccount(EAAAccount eAAAccount) throws Exception {

		// bind the account to the newpeople ou
		ctx.bind("uid=" + eAAAccount.getUid() + "," + Constants.NEW_PEOPLE_DN,
				eAAAccount);

		// create a notification to inform the user
		//
		// The activation URL is:
		// https://umbrella.psi.ch/euu/validate?uid=<uid>&uuid=<uuid>
		//

		// Notification notification = new Notification();
		// notification.setSubject( "Please activate your Umbrella account" );
		// String body =
		// "Dear " + eAAAccount.getUid() + ",\n\nThe activation URL is: " +
		// Messages.getString( "eaa.url" )
		// + "euu/validate?uid=" + eAAAccount.getUid() + "&uuid=" +
		// eAAAccount.getEaahash();
		// if ( eAAAccount.getTarget() != null &&
		// !eAAAccount.getTarget().equals( "" ) )
		// {
		// body = body + "&target=" + eAAAccount.getTarget();
		// }
		// notification.setBody( body );
		// notificationService.notify( notification, eAAAccount );

		String activationURL = Messages.getString("eaa.url")
				+ "euu/validate?uid=" + eAAAccount.getUid() + "&uuid="
				+ eAAAccount.getEaahash();
		if (eAAAccount.getTarget() != null
				&& !eAAAccount.getTarget().equals("")) {
			activationURL = activationURL + "&target=" + eAAAccount.getTarget();
		}

		Notification notification = new Notification();
		notification.setSubject(Messages.getString("subject.createaccount"));

		HashMap<String, String> rep = new HashMap<String, String>();
		rep.put("eAAAccount.getUid()", eAAAccount.getUid());
		rep.put("activationURL", activationURL);

		String body = Messages.replace(
				Messages.getString("body.createaccount"), rep);
		notification.setBody(body);

		File file = new File(Messages.getString("mail.template.path"));
		String layout = FileUtils.readFileToString(file);
		notification.setLayout(layout);
		notificationService.notify(notification, eAAAccount);

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * eu.eurofel.services.EAAService#activateAccount(eu.eurofel.entities.EAAAccount
	 * )
	 */

	public boolean changeEmail(String uid, String uuid, String email)
			throws Exception {
		Date date = new Date();
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);

		int year = calendar.get(Calendar.YEAR);
		int month = calendar.get(Calendar.MONTH);
		int day = calendar.get(Calendar.DAY_OF_MONTH);
		int hour = calendar.get(Calendar.HOUR_OF_DAY);

		// check if new email already is registerd
		Attributes at = null;
		try {
			at = findAccountByEmail(email);
		} catch (Exception e) {
		}
		if (at != null) {
//			System.out.println();
			throw new Exception("E-Mail already exists!");
		}

		EAAAccount eAAAccount = new EAAAccount(findAccountByHash(uuid));
		EAAAccount ea1 = new EAAAccount();
		ea1.setEmail(email);

		String checksum = EAAHash.getSHA1Hash(email + eAAAccount.getEaakey()
				+ year + month + day + hour);

		String changelink = Messages.getString("eaa.url")
				+ "euu/changemail?uuid=" + uuid + "&email=" + email
				+ "&checksum=" + checksum;
		// create a notification to verify the email
		//
		// The activation URL is:
		// https://umbrella.psi.ch/euu/validate?uid=<uid>&uuid=<uuid>
		//
		Notification notification = new Notification();
		notification.setSubject(Messages.getString("subject.changemail"));

		HashMap<String, String> rep = new HashMap<String, String>();
		rep.put("eAAAccount.getUid()", uid);
		rep.put("getEmail()", email);
		rep.put("changelink", changelink);
//		System.out.println(changelink);

		String body = Messages.replace(Messages.getString("body.changemail"),
				rep);
		notification.setBody(body);

		File file = new File(Messages.getString("mail.template.path"));
		String layout = FileUtils.readFileToString(file);
		notification.setLayout(layout);
		EAAAccount mailAcc = new EAAAccount();
		mailAcc.setEmail(email);
		notificationService.notify(notification, mailAcc);
		return false;
	}

	public void createBridge(BridgeFederation federationBridge)
			throws Exception {

		// bind the account to the bridge ou with a distinct uid == UUID
//		System.out.println("CONSTANTS: " + Constants.BRIDGE_DN);
		ctx.bind("uid=" + UUID.randomUUID().toString() + ","
				+ Constants.BRIDGE_DN, federationBridge);

	}

	public boolean removeBridge(String uid) {
		String dn = uid + "," + Constants.BRIDGE_DN;
		try {
			ctx.unbind(dn);
		} catch (NamingException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	public void activateAccount(EAAAccount eAAAccount) throws NamingException {

		ctx.rename(
				"uid=" + eAAAccount.getUid() + "," + Constants.NEW_PEOPLE_DN,
				"uid=" + eAAAccount.getUid() + "," + Constants.PEOPLE_DN);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * eu.eurofel.services.EAAService#updateAccount(eu.eurofel.entities.EAAAccount
	 * )
	 */

	public void updateAccount(EAAAccount eAAAccount) throws NamingException {

//		System.out.println("eAAAccount:: " + eAAAccount);
		if (eAAAccount != null) {
//			System.out.println(eAAAccount.getGivenName());
		}
		// Collect changes to eAccount

		EAAAccount acc = new EAAAccount(this.findAccountByHash(eAAAccount
				.getEaahash()));
		HashMap<String, String> map = new HashMap<String, String>();

		if (eAAAccount.getGivenName() != null
				&& !eAAAccount.getGivenName().equals(acc.getGivenName())) {
			map.put("givenName", eAAAccount.getGivenName());
		} else if (eAAAccount.getGivenName() == null) {
			map.put("givenName", null);
		}

		if (eAAAccount.getEmailAddress() != null
				&& !eAAAccount.getEmailAddress().equals(acc.getEmailAddress())) {
			map.put("emailAddress", eAAAccount.getEmailAddress());
		} else if (eAAAccount.getEmailAddress() == null) {
			map.put("emailAddress", null);
		}

		if (eAAAccount.getHomePhone() != null
				&& !eAAAccount.getHomePhone().equals(acc.getHomePhone())) {
			map.put("homePhone", eAAAccount.getHomePhone());
		} else if (eAAAccount.getHomePhone() == null) {
			map.put("homePhone", null);
		}

		if (eAAAccount.getHomePostalAddress() != null
				&& !eAAAccount.getHomePostalAddress().equals(
						acc.getHomePostalAddress())) {
			map.put("homePostalAddress", eAAAccount.getHomePostalAddress());
		} else if (eAAAccount.getHomePostalAddress() == null) {
			map.put("homePostalAddress", null);
		}

		if (eAAAccount.getMobile() != null
				&& !eAAAccount.getMobile().equals(acc.getMobile())) {
			map.put("mobile", eAAAccount.getMobile());
		} else if (eAAAccount.getMobile() == null) {
			map.put("mobile", null);
		}

		if (eAAAccount.getSn() != null
				&& !eAAAccount.getSn().equals(acc.getSn())) {
			map.put("sn", eAAAccount.getSn());
		} else if (eAAAccount.getSn() == null) {
			map.put("sn", null);
		}

		if (eAAAccount.getPostalAddress() != null
				&& !eAAAccount.getPostalAddress()
						.equals(acc.getPostalAddress())) {
			map.put("postalAddress", eAAAccount.getPostalAddress());
		} else if (eAAAccount.getPostalAddress() == null) {
			map.put("postalAddress", null);
		}

		if (eAAAccount.getTelephoneNumber() != null
				&& !eAAAccount.getTelephoneNumber().equals(
						acc.getTelephoneNumber())) {
			map.put("telephoneNumber", eAAAccount.getTelephoneNumber());
		} else if (eAAAccount.getTelephoneNumber() == null) {
			map.put("telephoneNumber", null);
		}

		if (eAAAccount.getOrcid() != null
				&& !eAAAccount.getOrcid().equals(
						acc.getOrcid())) {
			map.put("eduPersonOrcid", eAAAccount.getOrcid());
		} else if (eAAAccount.getOrcid() == null) {
			map.put("eduPersonOrcid", null);
		}
		ModificationItem[] mods = new ModificationItem[map.size()];

		Iterator<Map.Entry<String, String>> it = map.entrySet().iterator();
		int i = 0;
		while (it.hasNext()) {
			Map.Entry<String, String> pair = (Map.Entry<String, String>) it
					.next();

			Attribute mod0 = new BasicAttribute(pair.getKey(), pair.getValue());
			mods[i] = new ModificationItem(DirContext.REPLACE_ATTRIBUTE, mod0);
			i += 1;
		}
		try {
			ctx.modifyAttributes(
					"uid=" + acc.getUid() + ","
							+ eurofel.getString("eaa.people_root"), mods);
		} catch (NamingException e) {
			e.printStackTrace();
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see eu.eurofel.services.EAAService#findAccount(java.lang.String)
	 */

	public Attributes findAccount(String uid) throws NamingException {
		String query = "uid=" + uid;
		SearchControls ctrl = new SearchControls();
		ctrl.setSearchScope(SearchControls.SUBTREE_SCOPE);
		Attributes attrs;
		try {
			attrs = ctx.search("", query, ctrl).next().getAttributes();
		} catch (Exception e) {
			throw new NamingException(e.getLocalizedMessage());
		}
		return attrs;

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see eu.eurofel.services.EAAService#isAccountAvailable(java.lang.String)
	 */

	public boolean isAccountAvailable(String uid) throws NamingException {

		String query = "uid=" + uid;
		SearchControls ctrl = new SearchControls();
		ctrl.setSearchScope(SearchControls.SUBTREE_SCOPE);
		NamingEnumeration<SearchResult> enumeration = ctx.search("", query,
				ctrl);
		if (enumeration.hasMore()) {
			throw new NamingException("Account exists");
		} else {
			return false;
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see eu.eurofel.services.EAAService#isEmailAvailable(java.lang.String)
	 */

	public boolean isEmailAvailable(String email) throws NamingException {
		email = EAAHash.getSHA1Hash(email.toLowerCase());
		String query = "mail=" + email;
		SearchControls ctrl = new SearchControls();
		ctrl.setSearchScope(SearchControls.SUBTREE_SCOPE);
		NamingEnumeration<SearchResult> enumeration = ctx.search("", query,
				ctrl);
		while (enumeration.hasMore()) {
			// loop through the results and find the email
			EAAAccount acc = new EAAAccount(enumeration.next().getAttributes());
			if (acc.getEmail().equals(email)) {
				throw new NamingException("Email exists");
			}
		}
		return false;

	}

	public DisseminationService getDisseminationService() {
		return disseminationService;
	}

	public void setDisseminationService(
			DisseminationService disseminationService) {
		this.disseminationService = disseminationService;
	}

	public void setNotificationService(NotificationService notificationService) {
		this.notificationService = notificationService;
	}

	public Attributes findAccountByHash(String eaahash) throws NamingException {

		String query = "(EAAHash=" + eaahash + ")";
		SearchControls ctrl = new SearchControls();
		ctrl.setSearchScope(SearchControls.SUBTREE_SCOPE);
		Attributes attrs;
		try {
			attrs = ctx.search("", query, ctrl).next().getAttributes();
		} catch (Exception e) {
			e.printStackTrace();
			throw new NamingException(e.getLocalizedMessage());
		}
		return attrs;
	}

	public boolean hasAccountEmail(String hash, String email)
			throws NamingException {
		String query = "(&(mail=" + email + ") (EAAHash=" + hash + "))";
		SearchControls ctrl = new SearchControls();
		ctrl.setSearchScope(SearchControls.SUBTREE_SCOPE);
		NamingEnumeration<SearchResult> enumeration = ctx.search("", query,
				ctrl);
		if (enumeration.hasMore()) {
			return true;
		} else {
			throw new NamingException("Wrong email for this account");
		}

	}

	public boolean validatePassword(String uid, String pwd) {

		Hashtable<String, String> env = new Hashtable<String, String>();
		env.put(Context.INITIAL_CONTEXT_FACTORY,
				eurofel.getString("eaa.initial_context_factory"));
		env.put(Context.PROVIDER_URL, eurofel.getString("eaa.provider_url"));
		env.put(Context.SECURITY_AUTHENTICATION,
				eurofel.getString("eaa.security_authentication"));
		env.put(Context.SECURITY_PRINCIPAL,
				"uid=" + uid + "," + eurofel.getString("eaa.people_root")); // specify
		// the
		// username
		env.put(Context.SECURITY_CREDENTIALS, pwd);

		try {
			// Create initial context
			DirContext ctx = new InitialDirContext(env);

			// Close the context when we're done
			ctx.close();
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	public void sendUsernameToEmail(String email) {

	}

	public boolean changePassword(String uid, String oldpwd, String newpwd) {

		if (validatePassword(uid, oldpwd)) {
			ModificationItem[] mods = new ModificationItem[1];

			Attribute mod0 = new BasicAttribute("userPassword", newpwd);

			mods[0] = new ModificationItem(DirContext.REPLACE_ATTRIBUTE, mod0);

			try {
				ctx.modifyAttributes(
						"uid=" + uid + ","
								+ eurofel.getString("eaa.people_root"), mods);
				return true;
			} catch (NamingException e) {
				e.printStackTrace();
				return false;
			}
		}
		return false;
	}

	public Attributes findAccountByEmail(String email) throws NamingException {
		String query = "(mail=" + EAAHash.getSHA1Hash(email.toLowerCase())
				+ ")";
		SearchControls ctrl = new SearchControls();
		ctrl.setSearchScope(SearchControls.SUBTREE_SCOPE);
		Attributes attrs;
		try {
			attrs = ctx.search("", query, ctrl).next().getAttributes();
		} catch (Exception e) {
			throw new NamingException(e.getLocalizedMessage());
		}
		return attrs;
	}

	public Attributes findAccountByEmailAndUid(String email, String uid)
			throws NamingException {
		String query = "(&(mail=" + EAAHash.getSHA1Hash(email.toLowerCase())
				+ ")(uid=" + uid + "))";
//		System.out.println(query);
		SearchControls ctrl = new SearchControls();
		ctrl.setSearchScope(SearchControls.SUBTREE_SCOPE);
		Attributes attrs;
		try {
			attrs = ctx.search("", query, ctrl).next().getAttributes();
		} catch (Exception e) {
			throw new NamingException(e.getLocalizedMessage());
		}
		return attrs;
	}

	public boolean changeEmail(String uid, String newemail) {

		ModificationItem[] mods = new ModificationItem[1];
		Attribute mod0 = new BasicAttribute("mail", EAAHash.getSHA1Hash(newemail));
		mods[0] = new ModificationItem(DirContext.REPLACE_ATTRIBUTE, mod0);
		try {
			ctx.modifyAttributes(
					"uid=" + uid + "," + eurofel.getString("eaa.people_root"),
					mods);
			return true;
		} catch (NamingException e) {
			e.printStackTrace();
			return false;
		}
	}

	public boolean changePassword(String uid, String newpwd) {

		ModificationItem[] mods = new ModificationItem[1];
		Attribute mod0 = new BasicAttribute("userPassword", newpwd);
		mods[0] = new ModificationItem(DirContext.REPLACE_ATTRIBUTE, mod0);
		try {
			ctx.modifyAttributes(
					"uid=" + uid + "," + eurofel.getString("eaa.people_root"),
					mods);
			return true;
		} catch (NamingException e) {
			e.printStackTrace();
			return false;
		}
	}

	public boolean addResetPwUUID(String uid, String uuid) {

		ModificationItem[] mods = new ModificationItem[2];
		Attribute mod0 = new BasicAttribute("EAAResetPwUUID", uuid);
		Attribute mod1 = new BasicAttribute("EAAResetPwDate", new Long(
				new Date().getTime()).toString());
		mods[0] = new ModificationItem(DirContext.REPLACE_ATTRIBUTE, mod0);
		mods[1] = new ModificationItem(DirContext.REPLACE_ATTRIBUTE, mod1);
		try {
			ctx.modifyAttributes(
					"uid=" + uid + "," + eurofel.getString("eaa.people_root"),
					mods);
			return true;
		} catch (NamingException e) {
			e.printStackTrace();
			return false;
		}
	}

	public boolean removeResetPwUUID(String uid) {

		ModificationItem[] mods = new ModificationItem[2];
		Attribute mod0 = new BasicAttribute("EAAResetPwUUID", "");
		Attribute mod1 = new BasicAttribute("EAAResetPwDate", "");
		mods[0] = new ModificationItem(DirContext.REMOVE_ATTRIBUTE, mod0);
		mods[1] = new ModificationItem(DirContext.REMOVE_ATTRIBUTE, mod1);
		try {
			ctx.modifyAttributes(
					"uid=" + uid + "," + eurofel.getString("eaa.people_root"),
					mods);
			return true;
		} catch (NamingException e) {
			e.printStackTrace();
			return false;
		}
	}

	public NamingEnumeration<?> findBridges(String eaahash) {
		// (BridgeFederationUmbrellaUID="5919673a-03c8-46bc-8870-1044e47f9072")
		String query = "(BridgeFederationUmbrellaUID=" + eaahash + ")";
//		System.out.println(query);
		SearchControls ctrl = new SearchControls();
		ctrl.setSearchScope(SearchControls.SUBTREE_SCOPE);

		NamingEnumeration<?> attrs = null;
		try {
			attrs = ctx.search(Constants.BRIDGE_DN, query, ctrl);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return attrs;
	}

	public NamingEnumeration<?> findBridgesByBridgeUid(String uid) {
		// (BridgeFederationUmbrellaUID="5919673a-03c8-46bc-8870-1044e47f9072")
		String query = "(BridgeFederationUID=" + uid + ")";
//		System.out.println(query);
		SearchControls ctrl = new SearchControls();
		ctrl.setSearchScope(SearchControls.SUBTREE_SCOPE);

		NamingEnumeration<?> attrs = null;
		try {
			attrs = ctx.search(Constants.BRIDGE_DN, query, ctrl);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return attrs;
	}

	public Attributes findAccountByResetUUID(String uuid, String uid,
			String email) throws NamingException {
		String query = "(&(mail=" + EAAHash.getSHA1Hash(email.toLowerCase())
				+ ")(uid=" + uid + ")(EAAResetPwUUID=" + uuid + "))";
//		System.out.println(query);
		SearchControls ctrl = new SearchControls();
		ctrl.setSearchScope(SearchControls.SUBTREE_SCOPE);
		Attributes attrs;
		try {
			attrs = ctx.search("", query, ctrl).next().getAttributes();
		} catch (Exception e) {
			throw new NamingException(e.getLocalizedMessage());
		}
		return attrs;
	}

	public boolean checkForValidResetUUID(String uuid) throws NamingException {
		String query = "(&(EAAResetPwUUID=" + uuid + "))";
		SearchControls ctrl = new SearchControls();
		ctrl.setSearchScope(SearchControls.SUBTREE_SCOPE);
		NamingEnumeration<SearchResult> enumeration = ctx.search("", query,
				ctrl);

		// check for date
		while (enumeration.hasMore()) {
			SearchResult result = enumeration.next();
			if (result.getAttributes() != null) {
				Attribute attr = result.getAttributes().get("EAAResetPWDate");
				if (attr != null) {

					long stamp = new Long(attr.get().toString()).longValue();
					;

					long date = new Date().getTime();
					if (date - stamp < 1800000 && date - stamp > 0) {
						return true;
					}
				}

			}
		}
		return false;

	}

	public void fetchLayout() {
		try {
			RetrieveNavigation.run();
		} catch (IllegalStateException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
