/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.cmakerunner.agent;

import jetbrains.buildServer.RunBuildException;
import jetbrains.buildServer.agent.messages.regex.ParserCommand;
import jetbrains.buildServer.agent.messages.regex.ParsersRegistry;
import jetbrains.buildServer.agent.runner.ProgramCommandLine;
import jetbrains.buildServer.agent.runner.SimpleProgramCommandLine;
import jetbrains.buildServer.cmakerunner.agent.util.OutputRedirectProcessor;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.teamcity.util.regex.ParserLoader;
import jetbrains.teamcity.util.regex.RegexParser;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static jetbrains.buildServer.cmakerunner.CMakeBuildConstants.*;

/**
 * @author : Vladislav.Rassokhin
 */
public class CMakeBuildBS extends ExtendedBuildServiceAdapter {

  @NotNull
  private static final String DEFAULT_CMAKE_PROGRAM = "cmake";
  private final ParsersRegistry myParsersRegistry;

  public CMakeBuildBS(@NotNull final ParsersRegistry parsersRegistry) {
    myParsersRegistry = parsersRegistry;
  }

  @NotNull
  @Override
  public ProgramCommandLine makeProgramCommandLine() throws RunBuildException {

    final List<String> arguments = new ArrayList<String>();
    final Map<String, String> runnerParameters = getRunnerParameters(); // all server-ui options
    final Map<String, String> environment = new HashMap<String, String>(System.getenv());
    environment.putAll(getBuildParameters().getEnvironmentVariables());

    // Path to 'cmake'
    String programPath = runnerParameters.get(UI_CMAKE_COMMAND);
    if (programPath == null) {
      programPath = DEFAULT_CMAKE_PROGRAM;
    }

    // Check for program exist
//    if (!FileUtil.checkIfExists(programPath) && FileUtil.findExecutableByNameInPATH(programPath, environment) == null)
//      throw new RunBuildException("Cannot locate `" + programPath + "' executable");


    // CMake options

    // Getting parameters
    final String buildPath = runnerParameters.get(UI_BUILD_PATH); // Directory contains CMakeCache.txt, etc.  relative to working directory
    final String buildTarget = runnerParameters.get(UI_BUILD_TARGET);
    final String buildConfiguration = runnerParameters.get(UI_BUILD_CONFIGURATION);
    final Boolean buildCleanFirst = Boolean.valueOf(runnerParameters.get(UI_BUILD_CLEAN_FIRST));


    arguments.add("--build");
    arguments.add(buildPath != null ? buildPath : "."); // May be removed, use working dir instead

    if (!StringUtil.isEmptyOrSpaces(buildTarget)) {
      arguments.add("--target");
      arguments.add(buildTarget);
    }
    if (!StringUtil.isEmptyOrSpaces(buildConfiguration)) {
      arguments.add("--config");
      arguments.add(buildConfiguration);
    }
    if (buildCleanFirst) {
      arguments.add("--clean-first");
    }

    // Native tool arguments
    arguments.add("--");
    addCustomArguments(arguments, runnerParameters.get(UI_NATIVE_TOOL_PARAMS));

    final RegexParser parser = ParserLoader.loadParser("/cmake-parser.xml", this.getClass());
    if (parser == null) {
      getLogger().message("Cannot load cmake parser");
    } else {
      myParsersRegistry.register(parser.getName(), parser);
      myParsersRegistry.enable(parser.getName(), ParserCommand.Scope.THIS_RUNNER);
    }

    final boolean redirectStdErr = Boolean.valueOf(runnerParameters.get(UI_REDIRECT_STDERR));
    // Result:
    final SimpleProgramCommandLine pcl = new SimpleProgramCommandLine(environment,
            getWorkingDirectory().getAbsolutePath(),
            programPath,
            arguments);
    return redirectStdErr ? OutputRedirectProcessor.wrap(getBuild(), pcl) : pcl;
  }
}


