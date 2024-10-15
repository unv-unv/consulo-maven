/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package consulo.maven.rt.server.common.server;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import consulo.maven.rt.server.common.util.MavenSystemInfo;
import jakarta.annotation.Nonnull;

public class MavenServerUtil
{
	private static final Properties ourSystemPropertiesCache;

	static
	{
		Properties res = new Properties();
		res.putAll(System.getProperties());

		for(Iterator<Object> itr = res.keySet().iterator(); itr.hasNext(); )
		{
			String propertyName = itr.next().toString();
			if(propertyName.startsWith("idea."))
			{
				itr.remove();
			}
		}

		for(Map.Entry<String, String> entry : System.getenv().entrySet())
		{
			String key = entry.getKey();

			if(isMagicalProperty(key))
			{
				continue;
			}

			if(MavenSystemInfo.isWindows)
			{
				key = key.toUpperCase();
			}

			res.setProperty("env." + key, entry.getValue());
		}

		ourSystemPropertiesCache = res;
	}

	public static Properties collectSystemProperties()
	{
		return ourSystemPropertiesCache;
	}

	@Nonnull
	public static File findMavenBasedir(@Nonnull File workingDir)
	{
		File baseDir = workingDir;
		File dir = workingDir;
		while((dir = dir.getParentFile()) != null)
		{
			if(new File(dir, ".mvn").exists())
			{
				baseDir = dir;
				break;
			}
		}
		try
		{
			return baseDir.getCanonicalFile();
		}
		catch(IOException e)
		{
			return baseDir.getAbsoluteFile();
		}
	}

	private static boolean isMagicalProperty(String key)
	{
		return key.startsWith("=");
	}

	public static void registerShutdownTask(Runnable task)
	{
		Runtime.getRuntime().addShutdownHook(new Thread(task, "Maven-server-shutdown-hook"));
	}
}