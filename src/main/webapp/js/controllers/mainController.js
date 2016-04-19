define([
	'jquery',
	'./websockets/webSocketController',
	'./tab/scenariosController',
	'./tab/defaultsController',
	'./tab/testsController',
	'text!../../templates/navbar.hbs',
	'text!../../templates/base.hbs',
	'text!../../templates/tab/buttons.hbs',
	'../util/handlebarsUtil',
	'../util/templatesUtil',
	'../util/cssUtil'
], function ($,
             webSocketController,
             scenariosController,
             defaultsController,
             testsController,
             navbarTemplate,
             baseTemplate,
             buttonsTemplate,
             hbUtil,
             templatesUtil,
             cssUtil) {
	
	const TAB_TYPE = templatesUtil.tabTypes();
	const BLOCK = templatesUtil.blocks();
	const plainId = templatesUtil.composeId;
	const jqId = templatesUtil.composeJqId;

	var currentTabType = TAB_TYPE.SCENARIOS;
	
	// render html and bind events of basic elements and same for all tabs elements 
	function render(scenariosArray, configObject) {
		const renderer = rendererFactory();
		renderer.navbar(configObject.run.version || 'unknown');
		renderer.base();
		renderer.buttons();
		scenariosController.render(scenariosArray);
		defaultsController.render(configObject);
		testsController.render();
		makeTabActive(currentTabType);
	}
	
	function tabJqId(tabType) {
		return jqId([tabType, 'tab']);
	}
	
	function makeTabActive(tabType) {
		const TAB_CLASS = templatesUtil.tabClasses();
		cssUtil.processClassElements('tab', tabType,
			function (elemSelector) {
			elemSelector.addClass(TAB_CLASS.ACTIVE);},
			function (elemSelector) {
			elemSelector.removeClass(TAB_CLASS.ACTIVE);});
		cssUtil.processClassElements('tab-dependent', tabType,
			function (elemSelector) {
				elemSelector.show();},
			function (elemSelector) {
				elemSelector.hide();});
		currentTabType = tabType;
	}
	
	const rendererFactory = function () {
		const binder = clickEventBinderFactory();
		const CONFIG_TABS = $.map(TAB_TYPE, function (value) { return value; }).slice(0, 2);
		function renderNavbar(runVersion) {
			hbUtil.compileAndInsertInsideBefore('body', navbarTemplate, {version: runVersion});
			binder.tab();
		}
		function renderBase() {
			hbUtil.compileAndInsertInside('#app', baseTemplate);
			const configElem = $('#config');
			$.each(CONFIG_TABS, function (index, value) {
				if (index === 0) {
					const detailsTree = $('<ul/>', {
						id: plainId([BLOCK.TREE, value, 'details']),
						class: BLOCK.TREE});
					configElem.after(detailsTree);
					detailsTree.hide();
				}
				configElem.after(
					$('<ul/>', {
						id: plainId([BLOCK.TREE, value]),
						class: BLOCK.TREE + ' ' + 'tab-dependent'}));
			})
		}
		function renderButtons() {
			// object to array
			const BUTTON_TYPE = templatesUtil.commonButtonTypes();
			const BUTTONS = $.map(BUTTON_TYPE, function (value) { return value; });
			$.each(CONFIG_TABS, function (index, value) {
				hbUtil.compileAndInsertInsideBefore('#config', buttonsTemplate,
					{ 'buttons': BUTTONS, 'tab-type': value });
			});
			binder.openButton(BUTTON_TYPE, CONFIG_TABS);
		}
		return {
			navbar: renderNavbar,
			base: renderBase,
			buttons: renderButtons
		}
	};
	
	const clickEventBinderFactory = function () {
		function bindTabClickEvent(tabType) {
			const tabId = tabJqId(tabType);
			$(tabId).click(function () {
				makeTabActive(tabType)
			});
		}
		
		function bindTabClickEvents() {
			$.each(TAB_TYPE, function (key, value) {
				bindTabClickEvent(value);
			});
		}
		
		function buttonJqId(buttonType, tabName) {
			return jqId([buttonType, tabName]);
		}
		
		function fillTheField(tabName, BUTTON_TYPE) {
			const openInputFileId = buttonJqId(BUTTON_TYPE.OPEN_INPUT_FILE, tabName);
			const openInputTextId = buttonJqId(BUTTON_TYPE.OPEN_INPUT_TEXT, tabName);
			const openFileName = $(openInputFileId).val();
			if (openFileName) {
				$(openInputTextId).val(openFileName)
			} else {
				$(openInputTextId).val('No ' + tabName.slice(0, -1) + ' chosen')
			}
		}
		
		function passClick(tabName, BUTTON_TYPE) {
			const openInputFileElem = $(buttonJqId(BUTTON_TYPE.OPEN_INPUT_FILE, tabName));
			$(buttonJqId(BUTTON_TYPE.OPEN, tabName)).click(function () {
				openInputFileElem.trigger('click');
			});
			openInputFileElem.change(function (data) {
				fillTheField(tabName, BUTTON_TYPE);
				openFileEvent(data);
			})
		}
		
		function bindOpenButtonClickEvents(BUTTON_TYPE, CONFIG_TABS) {
			$.each(CONFIG_TABS, function (index, value) {
				passClick(value, BUTTON_TYPE);
			});
		}

		var fullFileName = '';

		const reader = new FileReader();
		reader.onload = function (data) {
			content = data.target.result;
			const json = JSON.parse(content);
			// extendedConfController.openedJsonFileProcess(json, fullFileName);
		};
		reader.onerror = function (data) {
			console.error("File couldn't be read. Code" + data.target.error.code);
		};

		function openFileEvent(data) {
			const files = data.target.files; // FileList object
			const file = files[0];
			fullFileName = file.name;
			reader.readAsText(file);
		}
		
		return {
			tab: bindTabClickEvents,
			openButton: bindOpenButtonClickEvents
		}
	};
	
	return {
		render: render
	};
});