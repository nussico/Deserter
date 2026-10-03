package io.github.nussico.deserter.compat;

import net.fabricmc.fabric.api.permission.v1.PermissionContextOwner;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.permissions.PermissionSet;

/**
 * Permission nodes through Fabric's permission API, which Fabric API only ships from 26.1.2.
 * On older versions, falls back to the vanilla op level.
 */
public final class PermissionCompat {
	private static final boolean FABRIC_API = FabricLoader.getInstance().isModLoaded("fabric-permission-api-v1");

	private PermissionCompat() {
	}

	/**
	 * @param holder      a player or command source
	 * @param permissions its {@code permissions()}
	 */
	public static boolean check(Object holder, PermissionSet permissions, Identifier node, PermissionLevel fallback) {
		return FABRIC_API ? FabricPermissions.check(holder, node, fallback)
			: permissions.hasPermission(new Permission.HasCommandLevel(fallback));
	}

	/** Separate class so the permission API is only loaded when it's installed. */
	private static final class FabricPermissions {
		static boolean check(Object holder, Identifier node, PermissionLevel fallback) {
			return ((PermissionContextOwner) holder).checkPermission(node, fallback);
		}
	}
}
