/*
 * Copyright (c) 2016-2017 Daniel Ennis (Aikar) - MIT License
 *
 *  Permission is hereby granted, free of charge, to any person obtaining
 *  a copy of this software and associated documentation files (the
 *  "Software"), to deal in the Software without restriction, including
 *  without limitation the rights to use, copy, modify, merge, publish,
 *  distribute, sublicense, and/or sell copies of the Software, and to
 *  permit persons to whom the Software is furnished to do so, subject to
 *  the following conditions:
 *
 *  The above copyright notice and this permission notice shall be
 *  included in all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 *  EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 *  MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 *  NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 *  LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 *  OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 *  WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package co.aikar.commands;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import sun.reflect.generics.scope.Scope;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


@SuppressWarnings({"WeakerAccess", "UnusedReturnValue"})
public class CommandCompletions <I, C extends CommandCompletionContext> {
    private final CommandManager manager;
    private Map<String, CommandCompletionHandler> completionMap = new HashMap<>();

    public CommandCompletions(CommandManager manager) {
        this.manager = manager;
        registerCompletion("range", (sender, config, input, c) -> {
            if (config == null) {
                return ImmutableList.of();
            }
            final String[] ranges = ACFPatterns.DASH.split(config);
            int start;
            int end;
            if (ranges.length != 2) {
                start = 0;
                end = ACFUtil.parseInt(ranges[0], 0);
            } else {
                start = ACFUtil.parseInt(ranges[0], 0);
                end = ACFUtil.parseInt(ranges[1], 0);
            }
            return IntStream.rangeClosed(start, end).mapToObj(Integer::toString).collect(Collectors.toList());
        });
        registerCompletion("timeunits", (sender, config, input, c) -> ImmutableList.of("minutes", "hours", "days", "weeks", "months", "years"));
    }

    public CommandCompletionHandler registerCompletion(String id, CommandCompletionHandler<I, C> handler) {
        return this.completionMap.put("@" + id.toLowerCase(), handler);
    }

    @NotNull
    List<String> of(RegisteredCommand command, CommandIssuer sender, String[] completionInfo, String[] args) {
        final int argIndex = args.length - 1;

        String input = args[argIndex];
        final String completion = argIndex < completionInfo.length ? completionInfo[argIndex] : null;
        if (completion == null) {
            return ImmutableList.of(input);
        }

        return getCompletionValues(command, sender, completion, args);
    }

    @NotNull
    List<String> getCompletionValues(RegisteredCommand command, CommandIssuer sender, String completion, String[] args) {
        completion = manager.getCommandReplacements().replace(completion);
        final int argIndex = args.length - 1;

        String input = args[argIndex];
        List<String> allCompletions = Lists.newArrayList();

        for (String value : ACFPatterns.PIPE.split(completion)) {
            String[] complete = ACFPatterns.COLONEQUALS.split(value, 2);
            CommandCompletionHandler handler = this.completionMap.get(complete[0].toLowerCase());
            if (handler != null) {
                String config = complete.length == 1 ? null : complete[1];
                CommandCompletionContext context = manager.createCompletionContext(command, sender, input, config, args);

                try {
                    //noinspection unchecked
                    Collection<String> completions = handler.getCompletions(sender.getIssuer(), config, input, context);
                    if (completions != null) {
                        allCompletions.addAll(completions);
                        continue;
                    }
                    //noinspection ConstantIfStatement,ConstantConditions
                    if (false) { // Hack to fool compiler. since its sneakily thrown.
                        throw new CommandCompletionTextLookupException();
                    }
                } catch (CommandCompletionTextLookupException ignored) {
                    // This should only happen if some other feedback error occured.
                } catch (Exception e) {
                    command.handleException(sender, Lists.newArrayList(args), e);
                }
                // Something went wrong in lookup, fall back to input
                return ImmutableList.of(input);
            } else {
                // Plaintext value
                allCompletions.add(value);
            }
        }
        return allCompletions;
    }

    public interface CommandCompletionHandler <I, C extends CommandCompletionContext> {
        Collection<String> getCompletions(I sender, String config, String input, C context) throws InvalidCommandArgument;
    }

}
