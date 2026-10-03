package com.kuronami.jadeftbclaims.client;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec2;
import snownee.jade.api.config.IWailaConfig;
import snownee.jade.overlay.OverlayRenderer;
import snownee.jade.api.ui.Element;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** Jade row element for an owner's vanilla face and hat layer. */
final class OwnerFaceElement extends Element {
    private static final int FACE_SIZE = 9;
    private static final int MAX_CACHED_OWNERS = 128;
    private static final long LONG_TTL = TimeUnit.MINUTES.toNanos(30);
    private static final long SHORT_TTL = TimeUnit.MINUTES.toNanos(5);
    private static final Semaphore PROFILE_REQUESTS = new Semaphore(12);
    private static final Map<UUID, Entry> CACHE = new LinkedHashMap<>(32, .75f, true);

    private final GameProfile profile;

    OwnerFaceElement(GameProfile profile) {
        this.profile = profile;
        this.size = new Vec2(FACE_SIZE, FACE_SIZE);
    }

    @Override public Vec2 getSize() { return new Vec2(FACE_SIZE, FACE_SIZE); }

    @Override
    public void render(GuiGraphics graphics, float x, float y, float right, float bottom) {
        UUID ownerId = profile.getId();
        if (ownerId == null) return;
        ResourceLocation texture = null;
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.getConnection() != null) {
                PlayerInfo info = minecraft.getConnection().getPlayerInfo(ownerId);
                if (info != null && info.isSkinLoaded()) texture = info.getSkinLocation();
            }
            if (texture == null) texture = cachedTexture(minecraft, ownerId);
            if (texture == null) texture = DefaultPlayerSkin.getDefaultSkin(ownerId);
            float[] oldColor = RenderSystem.getShaderColor().clone();
            boolean pushed = false;
            try {
                float opacity = IWailaConfig.get().getOverlay().getAlpha() * OverlayRenderer.alpha;
                graphics.setColor(oldColor[0], oldColor[1], oldColor[2],
                        oldColor[3] * Math.max(0.0F, Math.min(1.0F, opacity)));
                graphics.pose().pushPose();
                pushed = true;
                graphics.pose().translate(x, y, 0);
                PlayerFaceRenderer.draw(graphics, texture, 0, 0, FACE_SIZE, true, false);
            } finally {
                if (pushed) graphics.pose().popPose();
                graphics.setColor(oldColor[0], oldColor[1], oldColor[2], oldColor[3]);
            }
        } catch (Throwable ignored) {
            // Skin/render failures must not suppress claim text or Jade rendering.
        }
    }

    private static ResourceLocation cachedTexture(Minecraft minecraft, UUID ownerId) {
        long now = System.nanoTime();
        Entry entry;
        synchronized (CACHE) {
            entry = CACHE.get(ownerId);
            if (entry == null || expired(entry, ownerId, now)) {
                GameProfile profile = new GameProfile(ownerId, "");
                entry = new Entry(resolveTexture(minecraft, profile, ownerId), now);
                CACHE.put(ownerId, entry);
                while (CACHE.size() > MAX_CACHED_OWNERS) CACHE.remove(CACHE.keySet().iterator().next());
            }
        }
        if (!entry.future.isDone() || entry.future.isCompletedExceptionally()) return null;
        return entry.future.getNow(null);
    }

    private static boolean expired(Entry entry, UUID ownerId, long now) {
        long age = now - entry.createdAt;
        if (age >= LONG_TTL) return true;
        if (!entry.future.isDone()) return false;
        try {
            ResourceLocation texture = entry.future.getNow(null);
            return texture == null || texture.equals(DefaultPlayerSkin.getDefaultSkin(ownerId))
                    ? age >= SHORT_TTL : false;
        } catch (Throwable ignored) {
            return age >= SHORT_TTL;
        }
    }

    private static CompletableFuture<ResourceLocation> resolveTexture(
            Minecraft minecraft, GameProfile profile, UUID ownerId) {
        CompletableFuture<GameProfile> resolved;
        if (profile.getProperties().containsKey("textures")) {
            resolved = CompletableFuture.completedFuture(profile);
        } else {
            resolved = CompletableFuture.supplyAsync(() -> {
                if (!PROFILE_REQUESTS.tryAcquire()) return profile;
                try {
                    GameProfile fetched = minecraft.getMinecraftSessionService().fillProfileProperties(profile, true);
                    return fetched != null && ownerId.equals(fetched.getId()) ? fetched : profile;
                } catch (Throwable ignored) {
                    return profile;
                } finally {
                    PROFILE_REQUESTS.release();
                }
            }, Util.backgroundExecutor());
        }
        return resolved.thenCompose(resolvedProfile -> registerSkin(minecraft, resolvedProfile, ownerId))
                .completeOnTimeout(DefaultPlayerSkin.getDefaultSkin(ownerId), 10, TimeUnit.SECONDS)
                .exceptionally(error -> DefaultPlayerSkin.getDefaultSkin(ownerId));
    }

    private static CompletableFuture<ResourceLocation> registerSkin(
            Minecraft minecraft, GameProfile profile, UUID ownerId) {
        CompletableFuture<ResourceLocation> result = new CompletableFuture<>();
        try {
            minecraft.getSkinManager().registerSkins(profile, (type, location, texture) -> {
                if (type == MinecraftProfileTexture.Type.SKIN) result.complete(location);
            }, true);
            CompletableFuture.delayedExecutor(10, TimeUnit.SECONDS).execute(
                    () -> result.complete(DefaultPlayerSkin.getDefaultSkin(ownerId)));
        } catch (Throwable ignored) {
            result.complete(DefaultPlayerSkin.getDefaultSkin(ownerId));
        }
        return result;
    }

    private static final class Entry {
        final CompletableFuture<ResourceLocation> future;
        final long createdAt;
        Entry(CompletableFuture<ResourceLocation> future, long createdAt) {
            this.future = future;
            this.createdAt = createdAt;
        }
    }
}
