/**
 * A page on screen. {@link dev.jasper.browser.page.Page} is what a browser item drives, in plain types: it makes the
 * page's canvas at once and its browser only once asked to start, in its browsing group's
 * {@link dev.jasper.browser.page.PageContext}, Chromium's global context or a /dev/null group's own in memory, and
 * tells its item its browser's identifier as Chromium makes it, on Chromium's thread.
 * {@link dev.jasper.browser.page.PageView} is the lightweight canvas Chromium's off-screen frames are painted on and its
 * input comes from, {@link dev.jasper.browser.page.PageRenderer} receives those frames, and
 * {@link dev.jasper.browser.page.PageBrowser} is the page's browser. {@link dev.jasper.browser.page.PageEvents} holds
 * the client's one display, load and keyboard handler, and a life span handler, which hand each page's callbacks to its
 * {@code Page} and refuse fullscreen, and tells the {@code links} package's hook each main frame's load start on
 * Chromium's thread; the main frame then runs {@code detect-favicon.js}, which names the document's icon through the
 * {@code links} package's message router, and
 * {@link dev.jasper.browser.page.Favicons} fetches it for the page. {@link dev.jasper.browser.page.NavigationHistory}
 * reads a page's history through DevTools after each main-frame load end and committed navigation, and a moment after a
 * title change, for its item's Back and Forward hold menus, and goes to the entry chosen; the {@code json} package
 * reads DevTools' answer. {@link dev.jasper.browser.page.PageFind} is find in page, behind the item's find bar:
 * {@code find.js} runs in each of the page's frames, highlights the matches and answers its count through the
 * {@code links} package's message router, and the {@code json} package writes the query into the script.
 * {@code PageText} puts text another plugin inserts where the user is typing in a page, through DevTools and never the
 * clipboard: {@code insert-text.js} runs in an isolated world of the main frame, and for a focus it cannot reach
 * {@code Input.insertText} types the text where the focus is; Return is pressed through DevTools too
 * ({@link dev.jasper.browser.page.Page#insertIntoField}, {@link dev.jasper.browser.page.Page#insertText},
 * {@link dev.jasper.browser.page.Page#pressReturn}). For copy on select, the canvas tells the page once the user has
 * finished a selection with the mouse or the keyboard ({@link dev.jasper.browser.page.Page#onSelectionGesture}), and
 * {@code PageText} reads it with {@code selection.js} in the same isolated world
 * ({@link dev.jasper.browser.page.Page#selectedText}).
 * {@link dev.jasper.browser.page.DialogPlace} is where the {@code dialogs} package shows a page's dialogs, as its item
 * gives it. {@code MediaAccess} is the client's one permission handler: a page's camera and microphone requests are its
 * top-level site's, as the {@code permissions} package names it, and go through the page's
 * {@link dev.jasper.browser.page.PagePrivacy} to its item, which answers them; screen capture, and any request of a
 * browser that is no page's, are cancelled. {@code FrameScripts} hooks each main-frame and sub-frame load start: a
 * page's main frame runs {@code notifications.js}, which stands in for {@code window.Notification} in the page's own
 * world, given the permission the item's store holds for the document's site through {@code PagePrivacy}, or denied
 * for a /dev/null page and outside a secure context, and asks Jasper through the {@code links} package's privacy
 * router; no sub-frame runs it, and no challenge frame ({@link dev.jasper.browser.page.ChallengeFrames}). Every frame
 * but a challenge frame runs {@code audio.js}, which mutes the document's sound and tells the privacy router of its
 * first, in the audio mode the item's store holds for the page's top-level site, which its sub-frames follow; the
 * item mutes and unmutes every frame through {@code PagePrivacy}. While ad blocking's filter list, the {@code adblock}
 * package's, has a rule set active, every frame but a challenge frame and a Jasper page's then runs
 * {@code hide-elements.js}, which hides what the rule set's sheet for the document names through
 * {@code CSSStyleSheet.insertRule}, its declaration fixed: a main frame its own sheet, a sub-frame the generic part. {@code PageMenu} is the client's
 * one context-menu handler: Jasper shows the page's right-click menu, {@code PageMenuItems} for what is under the
 * pointer, at the
 * {@link dev.jasper.browser.page.MenuPlace} the item gives, over its tab, and Chromium shows none; its View Source
 * reads the page's HTML through {@code Page.source}, and its Inspect Element opens the page's
 * {@link dev.jasper.browser.page.DevTools}: a canvas of their own, which the page makes, and Chromium's DevTools
 * browser, which the page's browser makes, on the page's client.
 * Each main-frame load start reaches its page with how the load reached it, the {@code store} package's
 * {@link dev.jasper.browser.store.Reached}, read from Chromium's transition, for its item's history
 * ({@link dev.jasper.browser.page.Page#onVisit}).
 * {@link dev.jasper.browser.page.LinkSchemes} decides which addresses a page's links open, and which a page may open
 * with Jasper's rights, for the {@code links} package and the menu. {@link dev.jasper.browser.page.LoadFailure} is a
 * main frame's failed load, as the load handler hands it to the page for its item's error page, with the
 * {@link dev.jasper.browser.page.ServerCertificate} Chromium could not verify for it, which the {@code links} package's
 * request handler goes on past only when the page's item allows it. The canvas, renderer and browser are adapted from
 * JetBrains' JCEF test classes, as {@code notices.toml} records. {@link dev.jasper.browser.page.PreviewPage} is a
 * Markdown preview's page, in plain types, for the {@code preview} package: it makes its canvas at once, and its browser,
 * on Chromium's preview client, only once the canvas first shows; {@code PreviewHandlers} are the preview client's own
 * handlers and message router, which refuse every navigation but the shell's first load, every request off the
 * preview's origin, popups, dialogs, the right-click menu and the camera and microphone, and its Find runs
 * {@code find.js} too.
 * {@link dev.jasper.browser.page.PageDownloads} is the client's one download handler: it hands the {@code downloads}
 * package each download Chromium starts as a {@link dev.jasper.browser.page.DownloadStart}, and continues it into the
 * {@link dev.jasper.browser.page.DownloadTarget} the answer names or refuses it, and each update as a
 * {@link dev.jasper.browser.page.DownloadProgress}, in plain values; the preview client has none, so CEF cancels a
 * preview's downloads. The right-click menu's Save Link As… and Save Image As… start a download that asks where it goes
 * ({@code DownloadAsk}), and a page that closes keeps its browser while its downloads run, as its close hold says
 * ({@link dev.jasper.browser.page.Page#setCloseHold}). A page's {@link dev.jasper.browser.page.PageContext} tells as its
 * group's store closes it, so the Downloads menu forgets a /dev/null group's downloads.
 * For the {@code tools} package, a page runs a script in its top frame ({@link dev.jasper.browser.page.Page#run}), but
 * never in a challenge provider's, tells each main-frame load end ({@link dev.jasper.browser.page.Page#onLoadEnd}),
 * hands on the reports its tools' scripts send through the {@code links} package's opener bridge
 * ({@link dev.jasper.browser.page.Page#onToolReport}), Save Page As's parts among them, and counts its documents
 * ({@link dev.jasper.browser.page.Page#document}), which tells Save Page As whether the page navigated meanwhile; the
 * right-click menu's Save Page As… is its item's, through the menu place.
 * For its item's password manager (plan 8), a page runs a login script in each http or https document of its main
 * frame as it starts loading, there and then on Chromium's thread, but never in a challenge provider's, and hands each
 * {@code login.pick} of its top frame, which the {@code links} package's opener bridge makes a
 * {@link dev.jasper.browser.page.PageQuery}, to its item's handler, to answer later, once, and only while the page
 * shows the origin that asked ({@link dev.jasper.browser.page.Page#handleLogins},
 * {@link dev.jasper.browser.page.Page#loginQuery}); {@code PageLoginHooks} holds the script and the handler, and the
 * page's close cancels the queries not answered. Each HTTP sign-in Chromium asks for one of a page's requests reaches
 * the page from the {@code links} package's request handler as a {@link dev.jasper.browser.page.SignIn}, answered
 * once, which the page hands its item, or cancels when nothing listens or it has closed
 * ({@link dev.jasper.browser.page.Page#onSignIn}); it is judged against the page's top address, the address of its
 * main frame's latest navigation request after its redirects, which that handler and the opener bridge note on
 * Chromium's IO thread ({@link dev.jasper.browser.page.Page#topAddress}), and the page tells its item each final
 * main-frame response's status ({@link dev.jasper.browser.page.Page#onMainFrameResponse}).
 * {@link dev.jasper.browser.page.LastPress}, {@code Favicons}, {@code DownloadStart}, {@code DownloadTarget},
 * {@code DownloadProgress}, {@code PageQuery}, {@code SignIn}, {@code Page}'s methods and {@code PreviewPage}'s name no
 * Chromium type,
 * and none of these classes loads until a browser item opens or a Markdown preview is made.
 */
package dev.jasper.browser.page;
