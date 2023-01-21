package consulo.maven.util;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.projectRoots.JavaSdk;
import com.intellij.java.language.projectRoots.JavaSdkVersion;
import consulo.content.bundle.Sdk;
import consulo.content.bundle.SdkTable;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * @author VISTALL
 * @since 2018-08-18
 */
public class MavenJdkUtil
{
	@Nonnull
	public static LanguageLevel getDefaultRunLevel(String currentMavenVersion)
	{
		boolean forceUseJava7 = StringUtil.compareVersionNumbers(currentMavenVersion, "3.3.1") >= 0;

		return forceUseJava7 ? LanguageLevel.JDK_1_7 : LanguageLevel.JDK_1_5;
	}

	@Nullable
	public static Sdk findSdkOfLevel(@Nonnull LanguageLevel languageLevel, @Nullable String runtimeJdkName)
	{
		SdkTable sdkTable = SdkTable.getInstance();
		JavaSdk javaSdk = JavaSdk.getInstance();
		if(runtimeJdkName != null)
		{
			Sdk sdk = sdkTable.findSdk(runtimeJdkName);
			if(sdk != null)
			{
				JavaSdkVersion version = javaSdk.getVersion(sdk);
				if(version != null && version.getMaxLanguageLevel().isAtLeast(languageLevel))
				{
					return sdk;
				}
			}
		}

		List<Sdk> list = sdkTable.getSdksOfType(javaSdk);
		ContainerUtil.weightSort(list, sdk ->
		{
			JavaSdkVersion version = javaSdk.getVersion(sdk);
			int ordinal = version == null ? 0 : version.ordinal();
			return sdk.isPredefined() ? ordinal * 100 : ordinal;
		});

		for(Sdk sdk : list)
		{
			JavaSdkVersion version = javaSdk.getVersion(sdk);
			if(version == null)
			{
				continue;
			}

			if(version.getMaxLanguageLevel().isAtLeast(languageLevel))
			{
				return sdk;
			}
		}

		return null;
	}
}
