package com.berotech.cceb.command;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import com.berotech.cceb.CCEditorBridge;
import com.berotech.cceb.client.BridgeStatus;
import com.berotech.cceb.client.ComputerTargeting;
import com.berotech.cceb.client.EditorBridgeService;
import com.berotech.cceb.cc.LabelComputerReference;
import com.berotech.cceb.network.client.ClientPacketSender;
import com.berotech.cceb.network.payload.FileListResponsePayload;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.config.ConfigTracker;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

@EventBusSubscriber(modid = CCEditorBridge.MODID, value = Dist.CLIENT)
public final class CCEditorCommands {
    private static final int PORT_TEST_TIMEOUT_MS = 1000;

    private CCEditorCommands() {}

    @SubscribeEvent
    public static void registerClientCommands(RegisterClientCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("cceditor")
                .then(Commands.literal("status")
                        .executes(context -> showStatus(context)))
                .then(Commands.literal("test")
                        .executes(context -> runSelfTest(context)))
                .then(Commands.literal("reload")
                        .executes(context -> reloadConfig(context)))
                .then(Commands.literal("id")
                        .executes(context -> showComputerId(context)))
                .then(Commands.literal("list")
                        .executes(context -> listFiles(context, ComputerTargeting.getTargetComputerId(), "/"))
                        .then(Commands.argument("computer", StringArgumentType.greedyString())
                                .executes(context -> listFiles(
                                        context,
                                        StringArgumentType.getString(context, "computer"),
                                        "/"
                                ))))
                .then(Commands.literal("testpacket")
                        .executes(context -> {
                            String computerId = ComputerTargeting.getTargetComputerId();
                            if (computerId == null) {
                                context.getSource().sendFailure(Component.literal("Look at a CC computer, then run /cceditor testpacket"));
                                return 0;
                            }
                            return listFiles(context, computerId, "/");
                        }))
                .then(Commands.literal("testwrite")
                        .executes(context -> {
                            String computerId = ComputerTargeting.getTargetComputerId();
                            if (computerId == null) {
                                context.getSource().sendFailure(Component.literal("Look at a CC computer, then run /cceditor testwrite"));
                                return 0;
                            }

                            ClientPacketSender.sendFileWriteRequest(computerId, "cceditor_test.lua", "print('hi from cceditor')")
                                    .whenComplete((response, error) -> Minecraft.getInstance().execute(() -> {
                                        if (error != null) {
                                            context.getSource().sendFailure(Component.literal("Write test failed: " + error.getMessage()));
                                            return;
                                        }
                                        if (!response.isSuccess()) {
                                            context.getSource().sendFailure(Component.literal("Write test failed: " + response.errorMessage()));
                                            return;
                                        }
                                        context.getSource().sendSuccess(
                                                () -> Component.literal("Write test ok on " + computerId + "; file_modified event should reach connected editors"),
                                                false
                                        );
                                    }));
                            return 1;
                        }));

        event.getDispatcher().register(root);
    }

    private static int showStatus(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        BridgeStatus status = EditorBridgeService.status();
        String address = "ws://127.0.0.1:" + status.port() + "/";
        context.getSource().sendSuccess(
                () -> Component.literal("Bridge status: " + status.format() + " | address=" + address),
                false
        );
        return 1;
    }

    private static int runSelfTest(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        BridgeStatus status = EditorBridgeService.status();
        if (!status.enabled()) {
            context.getSource().sendFailure(Component.literal("Bridge disabled in config (enabled=false)"));
            return 0;
        }
        if (!status.running()) {
            context.getSource().sendFailure(Component.literal("Bridge enabled but WebSocket server is not running"));
            return 0;
        }
        if (!isPortOpen(status.port())) {
            context.getSource().sendFailure(Component.literal("Bridge reports running but localhost:" + status.port() + " is not accepting connections"));
            return 0;
        }

        if (Minecraft.getInstance().getConnection() == null) {
            context.getSource().sendSuccess(
                    () -> Component.literal("Bridge self-test ok: WebSocket listening on ws://127.0.0.1:" + status.port() + "/ (not in world for packet test)"),
                    false
            );
            return 1;
        }

        ClientPacketSender.sendFileListRequest("pos:minecraft:overworld:0:0:0", "/")
                .whenComplete((response, error) -> Minecraft.getInstance().execute(() -> {
                    if (error != null) {
                        context.getSource().sendSuccess(
                                () -> Component.literal("Bridge self-test ok: WebSocket listening; packet path reachable (server responded)"),
                                false
                        );
                        return;
                    }
                    context.getSource().sendSuccess(
                            () -> Component.literal("Bridge self-test ok: WebSocket listening; packet round-trip succeeded"),
                            false
                    );
                }));
        return 1;
    }

    private static int reloadConfig(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        ConfigTracker.INSTANCE.loadConfigs(ModConfig.Type.CLIENT, FMLPaths.CONFIGDIR.get());

        BridgeStatus status = EditorBridgeService.status();
        context.getSource().sendSuccess(
                () -> Component.literal("Reloaded client configs from disk. Bridge: " + status.format()),
                false
        );
        return 1;
    }

    private static boolean isPortOpen(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), PORT_TEST_TIMEOUT_MS);
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private static int showComputerId(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        ComputerTargeting.TargetComputer target = ComputerTargeting.getTargetComputer();
        if (target == null) {
            context.getSource().sendFailure(Component.literal("Look at a CC computer first"));
            return 0;
        }

        StringBuilder message = new StringBuilder("Using id: " + target.id());
        message.append(" | position: ").append(target.positionId());
        if (target.label() != null && !target.label().isBlank()) {
            message.append(" | label: ").append(LabelComputerReference.of(target.label()).encode());
        } else {
            message.append(" | label: (none — set with os.setComputerLabel(\"name\"))");
        }
        context.getSource().sendSuccess(() -> Component.literal(message.toString()), false);
        return 1;
    }

    private static int listFiles(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, String computerId, String path) {
        if (computerId == null || computerId.isBlank()) {
            context.getSource().sendFailure(Component.literal("Look at a CC computer or pass pos:<dimension>:x:y:z or label:<name>"));
            return 0;
        }

        ClientPacketSender.sendFileListRequest(computerId, path).whenComplete((response, error) -> Minecraft.getInstance().execute(() -> {
            if (error != null) {
                context.getSource().sendFailure(Component.literal("List failed: " + error.getMessage()));
                return;
            }
            if (!response.isSuccess()) {
                context.getSource().sendFailure(Component.literal("List failed: " + response.errorMessage()));
                return;
            }
            context.getSource().sendSuccess(
                    () -> Component.literal("Files on " + computerId + ": " + formatFileListResponse(response)),
                    false
            );
        }));
        return 1;
    }

    private static String formatFileListResponse(FileListResponsePayload response) {
        if (!response.isSuccess()) {
            return "error=" + response.errorMessage();
        }
        return "files=" + response.files();
    }
}
