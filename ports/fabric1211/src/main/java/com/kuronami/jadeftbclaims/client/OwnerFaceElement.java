package com.kuronami.jadeftbclaims.client;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.Util;
import net.minecraft.world.phys.Vec2;
import snownee.jade.api.ui.IDisplayHelper;
import snownee.jade.api.ui.Element;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** Draws the owner's 8x8 face plus hat layer using Jade's own tooltip layout. */
final class OwnerFaceElement extends Element {
    private static final int FACE_SIZE = 9;
    private static final long CACHE_TTL_NANOS = TimeUnit.MINUTES.toNanos(30);
    private static final int MAX_CACHED_OWNERS = 128;
    private static final Semaphore PROFILE_REQUESTS = new Semaphore(12);

    private static final Map<UUID, CachedSkin> SKINS = new LinkedHashMap<>(32, 0.75f, true);

    private final GameProfile profile;

    OwnerFaceElement(GameProfile profile) {
        this.profile = profile;
        this.size = new Vec2(FACE_SIZE, FACE_SIZE);
    }

    @Override
    public Vec2 getSize() {
        return new Vec2(FACE_SIZE, FACE_SIZE);
    }

    @Override
    public void render(GuiGraphics graphics, float x, float y, float right, float bottom) {
        PlayerSkin skin = null;
        try {
            skin = onlineSkin();
        } catch (Throwable ignored) {
            // Continue to cached/default skin.
        }
        if (skin == null) {
            try {
                skin = cachedSkin();
            } catch (Throwable ignored) {
                // Continue to the UUID-specific default skin.
            }
        }
        if (skin == null) {
            try {
                skin = DefaultPlayerSkin.get(profile.getId());
            } catch (Throwable ignored) {
                return;
            }
        }
        if (skin == null) return;

        float[] oldColor = RenderSystem.getShaderColor().clone();
        boolean pushed = false;
        try {
            float opacity = IDisplayHelper.get().opacity();
            graphics.setColor(oldColor[0], oldColor[1], oldColor[2],
                    oldColor[3] * Math.max(0.0F, Math.min(1.0F, opacity)));
            graphics.pose().pushPose();
            pushed = true;
            graphics.pose().translate(x, y, 0);
            PlayerFaceRenderer.draw(graphics, skin.texture(), 0, 0, FACE_SIZE, true, false);
        } catch (Throwable ignored) {
            // A skin failure must never suppress the claim row or crash Jade rendering.
        } finally {
            if (pushed) graphics.pose().popPose();
            graphics.setColor(oldColor[0], oldColor[1], oldColor[2], oldColor[3]);
        }
    }

    /** Online PlayerInfo is already resolved and tracks skin changes immediately. */
    private PlayerSkin onlineSkin() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null) return null;
        PlayerInfo info = minecraft.getConnection().getPlayerInfo(profile.getId());
        return info == null ? null : info.getSkin();
    }

    /** Start at most one async profile/skin resolution per cached owner. */
    private PlayerSkin cachedSkin() {
        UUID ownerId = profile.getId();
        if (ownerId == null) return null;
        long now = System.nanoTime();
        CachedSkin entry;
        synchronized (SKINS) {
            entry = SKINS.get(ownerId);
            if (entry == null || isExpired(entry, ownerId, now)) {
                Minecraft minecraft = Minecraft.getInstance();
                CompletableFuture<PlayerSkin> future = resolveSkin(minecraft, ownerId);
                entry = new CachedSkin(future, now);
                SKINS.put(ownerId, entry);
                while (SKINS.size() > MAX_CACHED_OWNERS) {
                    SKINS.remove(SKINS.keySet().iterator().next());
                }
            }
        }
        if (!entry.future().isDone() || entry.future().isCompletedExceptionally()) return null;
        return entry.future().getNow(null);
    }

    private static boolean isExpired(CachedSkin entry, UUID ownerId, long now) {
        long age = now - entry.createdAtNanos();
        if (age >= CACHE_TTL_NANOS) return true;
        if (!entry.future().isDone()) return false;
        try {
            PlayerSkin skin = entry.future().getNow(null);
            return skin == null || skin.texture().equals(DefaultPlayerSkin.get(ownerId).texture())
                    ? age >= TimeUnit.MINUTES.toNanos(5)
                    : false;
        } catch (Throwable ignored) {
            return age >= TimeUnit.MINUTES.toNanos(5);
        }
    }

    /** Resolve missing texture properties on a worker, then let SkinManager load/cache the texture. */
    private CompletableFuture<PlayerSkin> resolveSkin(Minecraft minecraft, UUID ownerId) {
        try {
            if (minecraft.getMinecraftSessionService().getPackedTextures(profile) != null) {
                return minecraft.getSkinManager().getOrLoad(profile)
                        .exceptionally(error -> DefaultPlayerSkin.get(ownerId));
            }
        } catch (Throwable ignored) {
            // Treat malformed/missing local profile properties like an unresolved profile.
        }

        return CompletableFuture.supplyAsync(() -> {
                    if (!PROFILE_REQUESTS.tryAcquire()) return profile;
                    try {
                        ProfileResult result = minecraft.getMinecraftSessionService().fetchProfile(ownerId, true);
                        GameProfile resolved = result == null ? null : result.profile();
                        return resolved != null && ownerId.equals(resolved.getId()) ? resolved : profile;
                    } catch (Throwable ignored) {
                        return profile;
                    } finally {
                        PROFILE_REQUESTS.release();
                    }
                }, Util.backgroundExecutor())
                .thenCompose(minecraft.getSkinManager()::getOrLoad)
                .completeOnTimeout(DefaultPlayerSkin.get(ownerId), 10, TimeUnit.SECONDS)
                .exceptionally(error -> DefaultPlayerSkin.get(ownerId));
    }

    private record CachedSkin(CompletableFuture<PlayerSkin> future, long createdAtNanos) {}
}
