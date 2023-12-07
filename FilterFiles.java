
import java.io.*;
import java.util.regex.*;
import knižnica.*;
import static java.io.File.*;
import static java.lang.System.*;
import static knižnica.Súbor.*;
import static knižnica.Svet.*;

public class FilterFiles extends GRobot
{
	// historický spôsob zistenia cesty (keďže používame File)
	private static String getParentPath(String fileName)
	{
		File file = new File(fileName);

		if (null == file.getParent())
		{
			String absolutePath = file.getAbsolutePath();
			return absolutePath.substring(0,
				absolutePath.lastIndexOf(separator));
		}

		return file.getParent();
	}

	private static String[] args;

	private static String version = "FilterFiles 1.0, © 2022, Roman Horváth";

	private static void showHelp()
	{
		out.println();
		try {
			Súbor súbor = new Súbor();
			súbor.otvorNaČítanie("FileFilters-help-sk.txt");
			String help;
			while (null != (help = súbor.čítajRiadok()))
				out.println(help.replace("\t", "    "));
			súbor.zavri();
		} catch (IOException io) {
			out.println("Súbor pomocníka sa nepodarilo prečítať.");
			out.println(io.getMessage());
		}
	}


	private String filterPattern = null;
	private final Zoznam<String> sources = new Zoznam<>();
	private String outFile = "catalina-filtered.out";
	private final Zoznam<Pattern> start = new Zoznam<>();
	private final Zoznam<Pattern> end = new Zoznam<>();
	private final Zoznam<Pattern> pause = new Zoznam<>();
	private final Zoznam<Pattern> resume = new Zoznam<>();
	private final Zoznam<Filtre.Filter> filtre = new Zoznam<>();
	private String[] eq = {null, null, null, null, null,
		null, null, null, null, null};
	private String head = null, foot = null;
	private int filterReportLevel = 1;
	private int lineOut = -1;

	private static void out(String s1, String s2)
	{
		out.println(s1);
		vypíšRiadok(s2);
	}

	private String replaceVariables(String s)
	{
		int index = s.indexOf("#newline#");
		if (-1 != index)
		{
			String result = "";

			do {
				result += replaceVariables(s.substring(0, index)) + riadok;
				s = s.substring(index + 9);
				++lineOut;
				index = s.indexOf("#newline#");
			} while (-1 != index);

			return result + replaceVariables(s);
		}

		for (int i = 0; i < 10; ++i)
			if (null != eq[i])
				s = s.replace("#eq" + i + "#", eq[i]);

		if (-1 != s.indexOf("#line-") || -1 != s.indexOf("#line+"))
			for (int i = 1; i < 10; ++i)
				s = s.replace("#line-" + i + "#", "" + (lineOut - i))
					.replace("#line+" + i + "#", "" + (lineOut + i));

		return s.replace("#line#", "" + lineOut);
	}

	private void spracujParameter(String parameter)
	{
		if (null != filterPattern)
		{
			filtre.pridaj(new Filtre.Filter(filterPattern, parameter));
			filterPattern = null;
		}
		else if (parameter.startsWith(";")) { /* ignoruj */ }
		else if (parameter.startsWith("-in:"))
		{
			sources.pridaj(parameter.substring(4));
		}
		else if (parameter.startsWith("-out:"))
		{
			outFile = parameter.substring(5);
		}
		else if (parameter.startsWith("-start:"))
		{
			start.pridaj(Pattern.compile(parameter.substring(7)));
		}
		else if (parameter.startsWith("-end:"))
		{
			end.pridaj(Pattern.compile(parameter.substring(5)));
		}
		else if (parameter.startsWith("-pause:"))
		{
			pause.pridaj(Pattern.compile(parameter.substring(7)));
		}
		else if (parameter.startsWith("-resume:"))
		{
			resume.pridaj(Pattern.compile(parameter.substring(8)));
		}
		else if (parameter.startsWith("-filter:"))
		{
			filterPattern = parameter.substring(8);
		}
		else if (parameter.equals("-break"))
		{
			if (0 == filtre.size())
				out("invalid break", "neplatný break");
			else
			{
				Filtre.Filter f = filtre.posledný();
				f.zastav = true;
			}
		}
		else if (parameter.startsWith("-eq") && parameter.length() >= 5 &&
			':' == parameter.charAt(4))
		{
			int num = parameter.charAt(3) - '0';

			if (num < 0 || num > 9)
				out("invalid eq number (" + num + ")",
					S("neplatné číslo parametra eq: ", num));
			else
			{
				if (null != eq[num])
					out("warning: eq" + num + " was redefined",
						S("varovanie: rovnica eq" + num,
							" bola predefinovaná"));
				eq[num] = parameter.substring(5);
			}
		}
		else if (parameter.startsWith("-head:"))
		{
			head = parameter.substring(6);
		}
		else if (parameter.startsWith("-foot:"))
		{
			foot = parameter.substring(6);
		}
		else if (parameter.equals("-verboseFilters"))
		{
			filterReportLevel = 2;
		}
		else if (parameter.equals("-silenceFilters"))
		{
			filterReportLevel = 0;
		}
		else if (!parameter.isEmpty())
		{
			out("unknown parameter: " + parameter,
				"neznámy parameter: " + parameter);
		}
	}

	private FilterFiles() throws IOException
	{
		super(šírkaZariadenia(), výškaZariadenia(), version);

		skry();
		farbaTextu(čierna);
		Svet.písmo("Cambria", 32);
		skratkyStropu(true);
		strop.automatickéZobrazovanieLíšt(true);
		strop.zmeňOdsadenieSprava(30);

		// spracovanie vstupných parametrov
		for (String arg : args)
		{
			if (null == arg) continue;
			if (arg.startsWith("@"))
			{
				arg = arg.substring(1);
				if (!Súbor.jestvuje(arg))
					out("file of parameters is missing: " + arg,
						"súbor parametrov nejestvuje: " + arg);
				else try {
					súbor.otvorNaČítanie(arg);
					String parameter;
					while (null != (parameter = súbor.čítajRiadok()))
						spracujParameter(parameter);
					súbor.zavri();
				} catch (Throwable t) {
					out("io error while processing file of parameters: " + arg,
						"vznikla chyba počas spracovania súboru parametrov: " +
						arg);
					t.printStackTrace();
				}
			}
			else spracujParameter(arg);
		}

		// prerekvizity – kontrola plnenia základných vstupných podmienok
		{
			boolean sourcesEmpty = true;
			for (String source : sources)
				if (null != source && !source.isEmpty() && jestvuje(source))
				{
					sourcesEmpty = false;
					break;
				}
			if (sourcesEmpty)
				throw new Error("nebol zadaný vstupný priečinok alebo " +
					"súbor (zdroj)");
		}

		String filteredOut;

		{
			String path = ".";

			for (String source : sources)
				if (null != source && !source.isEmpty())
				{
					if (jestvuje(source))
					{
						if (jeSúbor(source))
							path = getParentPath(source);
						else
							path = source;
					}
					else
						out("warning: this source does not exist: " + source,
							"varovanie: priečinok alebo súbor nejestvuje: " +
							source);
				}

			filteredOut = path + separator + outFile;
		}

		if (jestvuje(filteredOut))
			throw new Error("súbor jestvuje: " + filteredOut);

		if (start.isEmpty())
			throw new Error("musí byť zadané aspoň jedno zapínacie kritérium");

		// krátky report o vstupných parametroch
		for (String source : sources)
			if (null != source && !source.isEmpty() && jestvuje(source))
				out("source: " + source, "zdroj: " + source);
		out("outFile: " + outFile, "cieľ: " + outFile);

		for (Pattern p : start)
			out("enable if: " + p.pattern(), "zapni ak: " + p.pattern());

		for (Pattern p : end)
			out("disable if: " + p.pattern(), "vypni ak: " + p.pattern());

		for (Pattern p : pause)
			out("pause if: " + p.pattern(), "pozastav ak: " + p.pattern());

		for (Pattern p : resume)
			out("resume if: " + p.pattern(), "obnov ak: " + p.pattern());

		for (Filtre.Filter f : filtre)
		{
			out("filter: " + f.vzor.pattern(), "filter: " + f.vzor.pattern());
			out("  replace: " + f.nahradenie, "  náhrada: " + f.nahradenie);
		}

		for (int i = 0; i < 10; ++i)
			if (null != eq[i])
			{
				String s = "eq[" + i + "]: " + eq[i];
				out(s, s);
			}

		if (null != head && !head.isEmpty())
			out("head: " + head, "hlavička: " + head);

		if (null != foot && !foot.isEmpty())
			out("foot: " + foot, "päta: " + foot);

		// čítanie a prepis do cieľového súboru
		Súbor vstup = new Súbor();
		Súbor výstup = new Súbor();
		výstup.otvorNaZápis(filteredOut, true);
		lineOut = 1;

		výstup.zapíšBOM();

		if (null != head && !head.isEmpty())
		{
			výstup.zapíšRiadok(replaceVariables(head));
			++lineOut;
		}

		for (String source : sources)
			if (null != source && !source.isEmpty() && jestvuje(source))
			{
				if (jeSúbor(source))
				{
					out("source: " + source, "súbor: " + source);
					vstup.otvorNaČítanie(source);
					spracujSúbor(vstup, výstup, null);
					vstup.zavri();
				}
				else
				{
					for (int i = 1; i <= 8; ++i)
					{
						String dirName = source + separator + "0" + i;
						if (!jestvuje(dirName))
							out("warning: a folder does not exist: " + dirName,
								"varovanie: priečinok nejestvuje: " + dirName);
						else
						{
							String sourceName = dirName + separator +
								"catalina.out";

							if (!jestvuje(sourceName))
								out("warning: a file does not exist: " +
									sourceName,
									"varovanie: súbor nejestvuje: " +
									sourceName);
							else
							{
								out("source: " + sourceName,
									"súbor: " + sourceName);
								vstup.otvorNaČítanie(sourceName);
								spracujSúbor(vstup, výstup, riadok +
									"catalina-0" + i + riadok);
								vstup.zavri();
							}
						}
					}
				}
			}

		if (null != foot && !foot.isEmpty())
		{
			výstup.zapíšRiadok(replaceVariables(foot));
			++lineOut;
		}

		výstup.zavri();
		out("finished", "dokončené");
		out("lines written: " + lineOut,
			S("počet zapísaných riadkov: ", lineOut));
	}

	private void spracujSúbor(Súbor vstup, Súbor výstup, String intro)
		throws IOException
	{
		String prečítané;
		boolean zapisuj = false;
		int lineIn = 1;
		boolean prvý = null != intro && !intro.isEmpty();

		while (null != (prečítané = vstup.čítajRiadok()))
		{
			boolean riadokVypnutia = false;

			if (zapisuj)
			{
				boolean vypni = !end.isEmpty();
				for (Pattern p : end)
					if (!p.matcher(prečítané).find())
					{
						vypni = false;
						break;
					}

				if (vypni)
				{
					out("disabled at: " + lineIn,
						S("vypnuté na riadku: ", lineIn));
					zapisuj = false;
					riadokVypnutia = true;
				}
				else
				{
					for (Pattern p : pause)
						if (p.matcher(prečítané).find())
						{
							vypni = true;
							break;
						}

					if (vypni)
					{
						out("paused at: " + lineIn,
							S("pozastavené na riadku: ", lineIn));
						zapisuj = false;
						riadokVypnutia = true; // (resp. pauzy)
					}
				}
			}

			if (!zapisuj) // (skrz prípadnú recykláciu riadkov)
			{
				boolean zapni = !start.isEmpty();
				for (Pattern p : start)
					if (!p.matcher(prečítané).find())
					{
						zapni = false;
						break;
					}

				if (zapni)
				{
					out("enabled at: " + lineIn,
						S("zapnuté na riadku: ", lineIn));
					if (prvý)
					{
						výstup.zapíšRiadok(intro);

						int počet = 1, count = intro.length();
						for (int i = 0; i < count; ++i)
							if ('\n' == intro.charAt(i)) ++počet;

						lineOut += počet;
						prvý = false;
					}
					zapisuj = true;
				}
				else
				{
					for (Pattern p : resume)
						if (p.matcher(prečítané).find())
						{
							zapni = true;
							break;
						}

					if (zapni)
					{
						out("resumed at: " + lineIn,
							S("obnovené na riadku: ", lineIn));
						if (prvý)
						{
							výstup.zapíšRiadok(intro);

							int počet = 1, count = intro.length();
							for (int i = 0; i < count; ++i)
								if ('\n' == intro.charAt(i)) ++počet;

							lineOut += počet;
							prvý = false;
						}
						zapisuj = true;
					}
				}
			}

			if (zapisuj || riadokVypnutia)
			{
				if (prečítané.isEmpty())
					out("skipped " + lineIn,
						S("riadok", lineIn, "preskočený"));
				else
				{
					for (Filtre.Filter f : filtre)
					{
						f.zhoda = f.zhoda(prečítané);

						if (f.zhoda.find())
						{
							String nové = replaceVariables(
								f.zhoda.replaceAll(f.nahradenie));

							// (-verboseFilters a -silenceFilters)
							if (filterReportLevel > 1)
								out("filter “" + f.vzor.pattern() +
									"” applied on " + lineIn + "\n  " +
									prečítané + "\n  " + nové,
									S("filter „", f.vzor.pattern(),
										"“ bol použitý na riadok ", lineIn,
										riadok, "  ", prečítané, riadok, "  ",
										nové));
							else if (filterReportLevel > 0)
								out("filter “" + f.vzor.pattern() +
									"” applied on " + lineIn,
									S("filter „", f.vzor.pattern(),
										"“ bol použitý na riadok ", lineIn));

							prečítané = nové;
							if (f.zastav) break;
						}
					}

					if (!prečítané.isEmpty())
					{
						výstup.zapíšRiadok(prečítané);
						++lineOut;
					}
				}
			}

			++lineIn;
		}

		out("lines read: " + lineIn, S("počet prečítaných riadkov: ", lineIn));
	}


	@Override public void tik()
	{
		if (neboloPrekreslené()) prekresli();
	}


	public static void main(String[] args)
	{
		out.println(version + ", " + versionString);

		if (null == args || 0 == args.length)
		{
			showHelp();
			return;
		}

		{
			boolean noArgs = true;

			for (String arg : args)
				if (null != arg && !arg.isEmpty())
				{
					if (arg.equals("/?") || arg.equals("-?") ||
						arg.equals("/help") || arg.equals("-help"))
					{
						showHelp();
						return;
					}

					noArgs = false;
				}

			if (noArgs)
			{
				showHelp();
				return;
			}
		}


		FilterFiles.args = args;
		použiKonfiguráciu("FilterFiles.cfg");
		Svet.skry();
		nekresli();

		try {
			new FilterFiles();
		} catch (Throwable t) {
			farbaTextu(červená);
			vypíšRiadok(t.getMessage());
			t.printStackTrace();
		} finally {
			Svet.zobraz();
			spustiČasovač();
			vykonaťNeskôr(() -> prekresli());
		}
	}
}
