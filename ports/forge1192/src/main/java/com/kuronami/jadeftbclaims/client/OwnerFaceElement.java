package com.kuronami.jadeftbclaims.client;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
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

/** 1.19.2 Jade row element using the vanilla skin atlas face and hat UVs. */
final class OwnerFaceElement extends Element {
    private static final int FACE_SIZE = 9;
    private static final int MAX_OWNERS = 128;
    private static final long SUCCESS_TTL = TimeUnit.MINUTES.toNanos(30);
    private static final long FALLBACK_TTL = TimeUnit.MINUTES.toNanos(5);
    private static final Semaphore PROFILE_REQUESTS = new Semaphore(12);
    private static final Map<UUID, CacheEntry> CACHE = new LinkedHashMap<>(32, 0.75f, true);

    private final GameProfile profile;

    OwnerFaceElement(GameProfile profile) {
        this.profile = profile;
        this.size = new Vec2(FACE_SIZE, FACE_SIZE);
    }

    @Override public Vec2 getSize() { return new Vec2(FACE_SIZE, FACE_SIZE); }

    @Override
    public void render(PoseStack pose, float x, float y, float right, float bottom) {
        UUID ownerId = profile.getId();
        if (ownerId == null) return;
        try {
            Minecraft minecraft = Minecraft.getInstance();
            ResourceLocation texture = onlineTexture(minecraft, ownerId);
            if (texture == null) texture = cachedTexture(minecraft, ownerId);
            if (texture == null) texture = DefaultPlayerSkin.getDefaultSkin(ownerId);
            if (texture == null) return;

            int oldTexture = RenderSystem.getShaderTexture(0);
            float[] oldColor = RenderSystem.getShaderColor().clone();
            boolean pushed = false;
            try {
                RenderSystem.setShaderTexture(0, texture);
                float jadeOpacity = IWailaConfig.get().getOverlay().getAlpha() * OverlayRenderer.alpha;
                RenderSystem.setShaderColor(oldColor[0], oldColor[1], oldColor[2],
                        oldColor[3] * Math.max(0.0F, Math.min(1.0F, jadeOpacity)));
                pose.pushPose();
                pushed = true;
                pose.translate(x, y, 0);
                PlayerFaceRenderer.draw(pose, 0, 0, FACE_SIZE, true, false);
            } finally {
                if (pushed) pose.popPose();
                RenderSystem.setShaderTexture(0, oldTexture);
                RenderSystem.setShaderColor(oldColor[0], oldColor[1], oldColor[2], oldColor[3]);
            }
        } catch (Throwable ignored) {
            // Face resolution/render failures never suppress the claim row.
        }
    }

    private static ResourceLocation onlineTexture(Minecraft minecraft, UUID ownerId) {
        if (minecraft.getConnection() == null) return null;
        PlayerInfo info = minecraft.getConnection().getPlayerInfo(ownerId);
        return info != null && info.isSkinLoaded() ? info.getSkinLocation() : null;
    }

    private static ResourceLocation cachedTexture(Minecraft minecraft, UUID ownerId) {
        long now = System.nanoTime();
        CacheEntry entry;
        synchronized (CACHE) {
            entry = CACHE.get(ownerId);
            if (entry == null || expired(entry, ownerId, now)) {
                GameProfile ownerProfile = new GameProfile(ownerId, "");
                entry = new CacheEntry(resolveTexture(minecraft, ownerProfile, ownerId), now);
                CACHE.put(ownerId, entry);
                while (CACHE.size() > MAX_OWNERS) CACHE.remove(CACHE.keySet().iterator().next());
            }
        }
        if (!entry.future.isDone() || entry.future.isCompletedExceptionally()) return null;
        return entry.future.getNow(null);
    }

    private static boolean expired(CacheEntry entry, UUID ownerId, long now) {
        long age = now - entry.createdAt;
        if (age >= SUCCESS_TTL) return true;
        if (!entry.future.isDone()) return false;
        try {
            ResourceLocation result = entry.future.getNow(null);
            return result == null || result.equals(DefaultPlayerSkin.getDefaultSkin(ownerId))
                    ? age >= FALLBACK_TTL : false;
        } catch (Throwable ignored) {
            return age >= FALLBACK_TTL;
        }
    }

    private static CompletableFuture<ResourceLocation> resolveTexture(
            Minecraft minecraft, GameProfile ownerProfile, UUID ownerId) {
        CompletableFuture<GameProfile> profileFuture;
        if (ownerProfile.getProperties().containsKey("textures")) {
            profileFuture = CompletableFuture.completedFuture(ownerProfile);
        } else {
            profileFuture = CompletableFuture.supplyAsync(() -> {
                if (!PROFILE_REQUESTS.tryAcquire()) return ownerProfile;
                try {
                    GameProfile fetched = minecraft.getMinecraftSessionService()
                            .fillProfileProperties(ownerProfile, true);
                    return fetched != null && ownerId.equals(fetched.getId()) ? fetched : ownerProfile;
                } catch (Throwable ignored) {
                    return ownerProfile;
                } finally {
                    PROFILE_REQUESTS.release();
                }
            }, Util.backgroundExecutor());
        }
        return profileFuture.thenCompose(profile -> registerSkin(minecraft, profile, ownerId))
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

    private static final class CacheEntry {
        final CompletableFuture<ResourceLocation> future;
        final long createdAt;
        CacheEntry(CompletableFuture<ResourceLocation> future, long createdAt) {
            this.future = future;
            this.createdAt = createdAt;
        }
    }
}
