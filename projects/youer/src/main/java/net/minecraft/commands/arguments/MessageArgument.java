package net.minecraft.commands.arguments;

import com.google.common.collect.Lists;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSigningContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.network.chat.ChatDecorator;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.FilteredText;

public class MessageArgument implements SignedArgument<MessageArgument.Message> {
    private static final Collection<String> EXAMPLES = Arrays.asList("Hello world!", "foo", "@e", "Hello @p :)");
    static final Dynamic2CommandExceptionType TOO_LONG = new Dynamic2CommandExceptionType(
        (p_341561_, p_341562_) -> Component.translatableEscape("argument.message.too_long", p_341561_, p_341562_)
    );

    public static MessageArgument message() {
        return new MessageArgument();
    }

    public static Component getMessage(CommandContext<CommandSourceStack> p_96836_, String p_96837_) throws CommandSyntaxException {
        MessageArgument.Message messageargument$message = p_96836_.getArgument(p_96837_, MessageArgument.Message.class);
        return messageargument$message.resolveComponent(p_96836_.getSource());
    }

    public static void resolveChatMessage(CommandContext<CommandSourceStack> p_249433_, String p_248718_, Consumer<PlayerChatMessage> p_249460_) throws CommandSyntaxException {
        MessageArgument.Message messageargument$message = p_249433_.getArgument(p_248718_, MessageArgument.Message.class);
        // Paper start
        resolveChatMessage(messageargument$message, p_249433_, p_248718_, p_249460_);
    }
    public static void resolveChatMessage(MessageArgument.Message p_249433_, CommandContext<CommandSourceStack> context, String p_248718_, Consumer<PlayerChatMessage> p_249460_) throws CommandSyntaxException {
        // Paper end
        CommandSourceStack commandsourcestack = context.getSource();
        Component component = p_249433_.resolveComponent(commandsourcestack);
        CommandSigningContext commandsigningcontext = commandsourcestack.getSigningContext();
        PlayerChatMessage playerchatmessage = commandsigningcontext.getArgument(p_248718_);
        if (playerchatmessage != null) {
            resolveSignedMessage(p_249460_, commandsourcestack, playerchatmessage.withUnsignedContent(component));
        } else {
            resolveDisguisedMessage(p_249460_, commandsourcestack, PlayerChatMessage.system(p_249433_.text).withUnsignedContent(component));
        }
    }

    private static void resolveSignedMessage(Consumer<PlayerChatMessage> p_250000_, CommandSourceStack p_252335_, PlayerChatMessage p_249420_) {
        MinecraftServer minecraftserver = p_252335_.getServer();
        CompletableFuture<FilteredText> completablefuture = filterPlainText(p_252335_, p_249420_);
        // Paper start - support asynchronous chat decoration
        CompletableFuture<Component> componentFuture = minecraftserver.getChatDecoratorPaper().decorate(p_252335_.getPlayer(), p_252335_, p_249420_.decoratedContent());
        p_252335_.getChatMessageChainer().append(CompletableFuture.allOf(completablefuture, componentFuture), filtered -> {
            PlayerChatMessage playerChatMessage2 = p_249420_.withUnsignedContent(componentFuture.join()).filter(completablefuture.join().mask());
            // Paper end - support asynchronous chat decoration
            p_250000_.accept(playerChatMessage2);
        });
    }

    private static void resolveDisguisedMessage(Consumer<PlayerChatMessage> p_249162_, CommandSourceStack p_248759_, PlayerChatMessage p_252332_) {
        ChatDecorator chatdecorator = p_248759_.getServer().getChatDecoratorPaper();
        // Paper start - support asynchronous chat decoration
        CompletableFuture<Component> componentFuture = chatdecorator.decorate(p_248759_.getPlayer(), p_248759_, p_252332_.decoratedContent());
        p_248759_.getChatMessageChainer().append(componentFuture, (result) -> p_249162_.accept(p_252332_.withUnsignedContent(result)));
        // Paper end - support asynchronous chat decoration
    }

    private static CompletableFuture<FilteredText> filterPlainText(CommandSourceStack p_252063_, PlayerChatMessage p_251184_) {
        ServerPlayer serverplayer = p_252063_.getPlayer();
        return serverplayer != null && p_251184_.hasSignatureFrom(serverplayer.getUUID())
            ? serverplayer.getTextFilter().processStreamMessage(p_251184_.signedContent())
            : CompletableFuture.completedFuture(FilteredText.passThrough(p_251184_.signedContent()));
    }

    public MessageArgument.Message parse(StringReader p_96834_) throws CommandSyntaxException {
        return MessageArgument.Message.parseText(p_96834_, true);
    }

    public <S> MessageArgument.Message parse(StringReader p_353137_, @Nullable S p_353119_) throws CommandSyntaxException {
        return MessageArgument.Message.parseText(p_353137_, EntitySelectorParser.allowSelectors(p_353119_));
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    public static record Message(String text, MessageArgument.Part[] parts) {
        Component resolveComponent(CommandSourceStack p_232197_) throws CommandSyntaxException {
            return this.toComponent(p_232197_, net.neoforged.neoforge.common.CommonHooks.canUseEntitySelectors(p_232197_));
        }

        public Component toComponent(CommandSourceStack p_96850_, boolean p_96851_) throws CommandSyntaxException {
            if (this.parts.length != 0 && p_96851_) {
                MutableComponent mutablecomponent = Component.literal(this.text.substring(0, this.parts[0].start()));
                int i = this.parts[0].start();

                for (MessageArgument.Part messageargument$part : this.parts) {
                    Component component = messageargument$part.toComponent(p_96850_);
                    if (i < messageargument$part.start()) {
                        mutablecomponent.append(this.text.substring(i, messageargument$part.start()));
                    }

                    mutablecomponent.append(component);
                    i = messageargument$part.end();
                }

                if (i < this.text.length()) {
                    mutablecomponent.append(this.text.substring(i));
                }

                return mutablecomponent;
            } else {
                return Component.literal(this.text);
            }
        }

        public static MessageArgument.Message parseText(StringReader p_96847_, boolean p_96848_) throws CommandSyntaxException {
            if (p_96847_.getRemainingLength() > 256) {
                throw MessageArgument.TOO_LONG.create(p_96847_.getRemainingLength(), 256);
            } else {
                String s = p_96847_.getRemaining();
                if (!p_96848_) {
                    p_96847_.setCursor(p_96847_.getTotalLength());
                    return new MessageArgument.Message(s, new MessageArgument.Part[0]);
                } else {
                    List<MessageArgument.Part> list = Lists.newArrayList();
                    int i = p_96847_.getCursor();

                    while (true) {
                        int j;
                        EntitySelector entityselector;
                        while (true) {
                            if (!p_96847_.canRead()) {
                                return new MessageArgument.Message(s, list.toArray(new MessageArgument.Part[0]));
                            }

                            if (p_96847_.peek() == '@') {
                                j = p_96847_.getCursor();

                                try {
                                    EntitySelectorParser entityselectorparser = new EntitySelectorParser(p_96847_, true);
                                    entityselector = entityselectorparser.parse();
                                    break;
                                } catch (CommandSyntaxException commandsyntaxexception) {
                                    if (commandsyntaxexception.getType() != EntitySelectorParser.ERROR_MISSING_SELECTOR_TYPE
                                        && commandsyntaxexception.getType() != EntitySelectorParser.ERROR_UNKNOWN_SELECTOR_TYPE) {
                                        throw commandsyntaxexception;
                                    }

                                    p_96847_.setCursor(j + 1);
                                }
                            } else {
                                p_96847_.skip();
                            }
                        }

                        list.add(new MessageArgument.Part(j - i, p_96847_.getCursor() - i, entityselector));
                    }
                }
            }
        }
    }

    public static record Part(int start, int end, EntitySelector selector) {
        public Component toComponent(CommandSourceStack p_96861_) throws CommandSyntaxException {
            return EntitySelector.joinNames(this.selector.findEntities(p_96861_));
        }
    }
}
