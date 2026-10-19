// glade: minecraft >=1.21.9
package net.tuffclient.compat;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.User;

/** An offline user (User lost its account type in 1.21.9). */
public final class Users {
	private Users() {
	}

	public static User offline(String name, UUID id) {
		return new User(name, id, "0", Optional.empty(), Optional.empty());
	}
}
