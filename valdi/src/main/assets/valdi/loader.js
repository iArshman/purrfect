(function () {
    'use strict';

    const runtimeName = _runtimeName;
    let remoteImports = null;
    for (const moduleName of ["DeviceBridge", "Device"]) {
        try {
            const imports = require(_runtimeName + "_core/" + moduleName)?.[_getImportsFunctionName]?.();
            if (imports != null) {
                remoteImports = imports;
                break;
            }
        }
        catch {
            // Some Snapchat builds expose DeviceBridge, others only Device.
        }
    }
    function callRemoteFunction(method, ...args) {
        try {
            const fn = remoteImports?.[method];
            if (typeof fn !== "function")
                return null;
            return fn(...args);
        }
        catch {
            return null;
        }
    }
    const log = (logLevel, message) => callRemoteFunction("log", logLevel, message);
    const getConfig = () => callRemoteFunction("getConfig");
    const downloadLastOperaMedia = (isLongPress) => callRemoteFunction("downloadLastOperaMedia", isLongPress);
    function getFriendOriginalUsername(username) {
        return callRemoteFunction("getFriendOriginalUsername", username);
    }
    function setEvalFunction(func) {
        callRemoteFunction("setEvalFunction", func);
    }

    const modules = [];
    function defineModule(module) {
        modules.push(module);
        return module;
    }

    const jsx = require(runtimeName + '_core/src/JSX').jsx;
    const assetCatalog = require(runtimeName + "_core/src/AssetCatalog");
    const style = require(runtimeName + "_core/src/Style");
    require("coreui/src/styles/semanticColors");

    function proxyProperty(module, functionName, handler) {
        if (!module || !module[functionName]) {
            console.warn("Function not found", functionName);
            return;
        }
        module[functionName] = new Proxy(module[functionName], {
            apply: (a, b, c) => handler(a, b, c),
            construct: (a, b, c) => handler(a, b, c)
        });
    }
    function interceptComponent(moduleName, className, functions) {
        proxyProperty(require(moduleName), className, (target, args, newTarget) => {
            let initProxy = functions["<init>"];
            let component;
            if (initProxy) {
                initProxy(args, (newArgs) => {
                    component = Reflect.construct(target, newArgs || args, newTarget);
                });
            }
            else {
                component = Reflect.construct(target, args, newTarget);
            }
            for (let funcName of Object.keys(functions)) {
                if (funcName == "<init>" || !component[funcName])
                    continue;
                proxyProperty(component, funcName, (target, thisArg, argumentsList) => {
                    let result;
                    try {
                        functions[funcName](component, argumentsList, (newArgs) => {
                            result = Reflect.apply(target, thisArg, newArgs || argumentsList);
                        });
                    }
                    catch (e) {
                        console.error("Error in", funcName, e);
                    }
                    return result;
                });
            }
            return component;
        });
    }

    defineModule({
        name: "Opera Download Button",
        enabled: config => config.operaDownloadButton,
        init() {
            interceptComponent('context_chrome_header/src/ChromeHeaderRenderer', 'ChromeHeaderRenderer', {
                onRenderBaseHeader: (_component, _args, render) => {
                    render();
                    jsx.beginRender(jsx.makeNodePrototype("image"));
                    jsx.setAttributeStyle("style", new style.Style({
                        height: 32,
                        marginTop: 4,
                        marginLeft: 8,
                        marginRight: 12,
                    }));
                    jsx.setAttribute("src", assetCatalog.loadCatalog("share_sheet/res").download);
                    jsx.setAttributeFunction("onTap", () => downloadLastOperaMedia(false));
                    jsx.setAttributeFunction("onLongPress", () => downloadLastOperaMedia(true));
                    jsx.endRender();
                }
            });
        }
    });

    defineModule({
        name: "Show First Created Username",
        enabled: config => config.showFirstCreatedUsername,
        init() {
            interceptComponent('common_profile/src/identity/ProfileIdentityView', 'ProfileIdentityView', {
                onRender: (component, _args, render) => {
                    if (component.viewModel) {
                        let firstCreatedUsername = getFriendOriginalUsername(component.viewModel.username);
                        if (firstCreatedUsername) {
                            if (firstCreatedUsername != component.viewModel.username) {
                                component.viewModel.username += " (" + firstCreatedUsername + ")";
                            }
                        }
                    }
                    render();
                }
            });
        }
    });

    defineModule({
        name: "Bypass Camera Roll Selection Limit",
        enabled: config => config.bypassCameraRollLimit,
        init() {
            try {
                interceptComponent('memories_ui/src/clickhandlers/MultiSelectClickHandler', 'MultiSelectClickHandler', {
                    "<init>": (args, superCall) => {
                        args[1].selectionLimit = 9999999;
                        superCall();
                    }
                });
            }
            catch (e) { }
            try {
                const { MemoriesPickerView } = require('memories/src/MemoriesPicker');
                proxyProperty(MemoriesPickerView, 'create', (target, thisArg, args) => {
                    if (args[1]?.m60491i)
                        args[1].m60491i(9999999);
                    return Reflect.apply(target, thisArg, args);
                });
            }
            catch (e) { }
        }
    });

    defineModule({
        name: "Self Destruct Snap Delay",
        enabled: config => config.customSelfDestructSnapDelay,
        init() {
            interceptComponent('snap_editor_timer_tool/src/TimerPickerView', 'TimerPickerView', {
                "<init>": (args, superCall) => {
                    if (args[1].options[0] == 30) {
                        args[1].style = 0; // seconds format
                        args[1].options = [
                            5, // 5 seconds
                            10, // 10 seconds
                            20, // 20 seconds
                            30, // 30 seconds
                            60, // 1 minute
                            120, // 2 minutes
                            180, // 3 minutes
                            240, // 4 minutes
                            300, // 5 minutes
                            600, // 10 minutes
                            900, // 15 minutes
                            1200, // 20 minutes
                            1800, // 30 minutes
                            3600, // 1 hour
                            7200, // 2 hours
                            10800, // 3 hours
                            14400, // 4 hours
                            21600, // 6 hours
                            28800, // 8 hours
                            43200, // 12 hours
                            86400, // 1 day
                            172800, // 2 days
                        ];
                    }
                    superCall();
                }
            });
        }
    });

    try {
        const config = getConfig();
        if (config.valdiLogs) {
            ["log", "error", "warn", "info", "debug"].forEach(method => {
                console[method] = (...args) => log(method, Array.from(args).map(arg => {
                    try {
                        return typeof arg === 'object' && arg !== null ? JSON.stringify(arg) : String(arg);
                    }
                    catch (e) {
                        return '[unserializable]';
                    }
                }).join(' '));
            });
        }
        modules.forEach(m => {
            if (!m.enabled(config)) {
                return;
            }
            try {
                m.init();
                console.debug(`module ${m.name} initialized`);
            }
            catch (e) {
                console.error(`failed to initialize module ${m.name}`, e, e.stack);
            }
        });
        setEvalFunction((code, callback) => {
            try {
                callback(eval(code)?.toString());
            }
            catch (e) {
                callback(e);
            }
        });
        console.debug("modules loaded!");
    }
    catch (e) {
        log("error", "Failed to load valdi modules\n" + e + "\n" + e.stack);
    }

})();
