# Política de privacidade do Minimalist

*Última atualização: 19 de outubro de 2026.* [English below](#minimalist-privacy-policy)

O Minimalist é uma tela inicial (launcher) para Android, gratuita, sem anúncios e de
código aberto. Ele não tem conta, servidor próprio, análise de uso, rastreamento nem
relatórios de erro. Nada do que ele lê é enviado ao desenvolvedor.

## O que o app lê, para quê e onde fica

Quase tudo é opcional e fica desligado até você ligar nas configurações do Minimalist.
Cada item usa uma permissão que o próprio Android pede ou que você concede nas
configurações do sistema, e que você pode retirar quando quiser.

| O quê | Para quê | Onde fica |
|---|---|---|
| **Lista de apps instalados** | Mostrar e buscar seus apps. | Só no aparelho. |
| **Contatos** (opcional) | Mostrar contatos na busca. | Lidos no aparelho na hora da busca. Não são salvos pelo app nem enviados. |
| **Acesso ao uso** (opcional) | Mostrar o tempo de tela do dia e o tempo num app antes da pausa consciente. | Só no aparelho. |
| **Acesso às notificações** (opcional) | Saber *quais apps* têm notificações esperando (os pontinhos e os ícones do relógio de cabeceira) e mostrar a música tocando (título, artista e controles). O texto das notificações não é lido. | Só no aparelho, na memória. |
| **Localização aproximada** (opcional) | Mostrar o clima. | A última posição (já arredondada a cerca de 1 km) e a última leitura ficam no aparelho e são apagadas quando você desliga o Clima nas duas telas. A posição arredondada é enviada à Open-Meteo (veja abaixo). |
| **País da rede ou do chip** | Escrever números e contas do jeito do seu país (vírgula ou ponto decimal). | Só no aparelho. |
| **Serviço de acessibilidade** (opcional) | Só duas coisas: bloquear a tela (toque duplo, ao sair do relógio de cabeceira ou depois de 10 segundos dele no carregador) e mostrar o relógio de cabeceira quando a tela acende. Para saber se há uma ligação em andamento (e nunca cobrir nem bloquear a tela de chamada), o app olha o modo de áudio do aparelho. O app avisa isso e pede sua concordância antes de abrir a página do Android. O serviço não lê o que aparece na tela, não vê o que você digita e não observa outros apps. | Não guarda nada. |
| **Digital, rosto ou PIN** | Confirmar que é você ao sair do relógio de cabeceira. | Feito pelo Android. O app só fica sabendo se deu certo, nunca recebe a digital nem o PIN. |
| **Atalhos dos apps** (por exemplo as conversas do WhatsApp) e **próximo alarme** | Mostrar os atalhos no menu de um app e o horário do próximo alarme ao lado da data. | Lidos na hora, do Android. Não são salvos. |
| **Suas preferências** (apps fixados, nomes trocados, apps ocultos e opções) | Lembrar como você arrumou a tela. | Só no aparelho. Fora do backup do Android. |

## O que sai do aparelho

Só uma coisa, e só com o **Clima** ligado. No máximo a cada meia hora (a cada 10 minutos
enquanto os pedidos falham, por exemplo sem internet), o app pede a temperatura à
[Open-Meteo](https://open-meteo.com/) (api.open-meteo.com). O pedido leva a latitude e a
longitude arredondadas a duas casas decimais (cerca de 1 km) e a unidade de temperatura,
e se identifica só como "Minimalist", sem o modelo do aparelho. Como em qualquer conexão à internet, a Open-Meteo também vê o endereço
IP do aparelho. Ela é um serviço de terceiros, com seus próprios
[termos e política](https://open-meteo.com/en/terms). Desligue o Clima e nada sai.

Quando *você* escolhe um resultado da busca, o que você digitou vai para o app que o
abre: a busca na web para o navegador ou o app de busca, `yt …` para o YouTube, um
contato para o WhatsApp, o discador ou o app de mensagens. Isso é você abrindo outro app,
como faria sem o Minimalist; esses apps têm suas próprias políticas.

O Minimalist não vende, não compartilha e não usa dados para publicidade.

## Apagar os dados

Tudo o que o app guarda fica no aparelho. Para apagar, use *Configurações → Apps →
Minimalist → Armazenamento → Limpar dados*, ou desinstale o app.

## Crianças

O app não é voltado a crianças e não coleta dados de ninguém.

## Mudanças e contato

Mudanças nesta política aparecem neste arquivo, com a data acima, e o histórico fica no
repositório. Dúvidas ou problemas: abra uma issue em
<https://github.com/jszutkoski/Minimalist-Launcher/issues>.

---

# Minimalist privacy policy

*Last updated: 19 October 2026.*

Minimalist is a free, ad-free, open-source home screen (launcher) for Android. It has no
account, no server of its own, no analytics, no tracking and no crash reports. Nothing it
reads is sent to the developer.

## What the app reads, what for, and where it stays

Almost everything is optional and stays off until you turn it on in Minimalist's settings.
Each uses a permission that Android itself asks for, or that you grant in Android's
settings, and that you can take back at any time.

| What | What for | Where it stays |
|---|---|---|
| **Installed apps** | Showing and searching your apps. | On the phone only. |
| **Contacts** (optional) | Contacts in the search. | Read on the phone while you search. Not saved by the app, never sent. |
| **Usage access** (optional) | Today's screen time, and the time in an app before the mindful pause. | On the phone only. |
| **Notification access** (optional) | Knowing *which apps* have notifications waiting (the dots and the bedside clock's icons), and showing what is playing (title, artist and controls). The text of notifications is not read. | On the phone only, in memory. |
| **Approximate location** (optional) | The weather. | The last place (already rounded to about 1 km) and reading stay on the phone, and are deleted when you turn Weather off on both screens. The rounded place goes to Open-Meteo (see below). |
| **Network or SIM country** | Writing numbers and sums your country's way (decimal comma or point). | On the phone only. |
| **Accessibility service** (optional) | Only two things: locking the screen (double-tap, leaving the bedside clock, or after 10 seconds of it on the charger) and showing the bedside clock when the screen turns on. To know whether a call is under way (and never cover or lock the call screen), the app looks at the phone's audio mode. The app explains this and asks you to agree before it opens Android's page. The service doesn't read what is on screen, sees nothing you type and watches no other app. | Nothing kept. |
| **Fingerprint, face or PIN** | Making sure it is you when leaving the bedside clock. | Done by Android. The app only learns whether it worked, never the fingerprint or PIN. |
| **App shortcuts** (WhatsApp chats, for example) and **the next alarm** | Showing an app's shortcuts in its menu, and the next alarm's time beside the date. | Read from Android when shown. Not saved. |
| **Your preferences** (pinned apps, renamed apps, hidden apps, options) | Remembering how you arranged things. | On the phone only. Left out of Android's backup. |

## What leaves the phone

One thing, and only with **Weather** on. At most every half hour (every 10 minutes while
requests fail, with no connection for example) the app asks
[Open-Meteo](https://open-meteo.com/) (api.open-meteo.com) for the temperature. The request
carries the latitude and longitude rounded to two decimals (about 1 km) and the
temperature unit, and names itself only "Minimalist", without the phone's model. As with any internet connection, Open-Meteo also sees the phone's IP
address. It is a third-party service with its own [terms and policy](https://open-meteo.com/en/terms).
Turn Weather off and nothing leaves.

When *you* pick a search result, what you typed goes to the app that opens it: a web
search to the browser or search app, `yt …` to YouTube, a contact to WhatsApp, the dialer
or messages. That is you opening another app, as you would without Minimalist; those apps
have their own policies.

Minimalist doesn't sell or share any data, nor use it for advertising.

## Deleting the data

Everything the app keeps is on the phone. To delete it, use *Settings → Apps → Minimalist
→ Storage → Clear storage*, or uninstall the app.

## Children

The app is not aimed at children and collects no one's data.

## Changes and contact

Changes to this policy appear in this file, with the date above, and its history stays in
the repository. Questions or problems: open an issue at
<https://github.com/jszutkoski/Minimalist-Launcher/issues>.
