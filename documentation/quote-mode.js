console.log('quote-mode');

eidosmedia.webclient.commands.add({
    name: 'custom.quotemode',


    isActive: function( ctx )
	{
   	// debugger;
	console.log("privilege " + eidosmedia.webclient.util.PrivilegesManager.check('_padmaPriv_ofextensionmodeCitation') );

	return eidosmedia.webclient.util.PrivilegesManager.check('_padmaPriv_ofextensionmodeCitation');
	},



	isEnabled: function( ctx ) {
        return true;
    },

	action: function( ctx, params, callbacks ) {


		/////////////////// FUNCTIONS /////////////////////////
		/**
		 * Read the "mode citation" rules file, parses it, and return the corresponding javascript object
		 * @param ctx
		 * @returns
		 */
		let readQuoteModeRulesFile = function (ctx) {
			var allRules;

			try {
				// since Swing 5.2019.?? when the format is set to "xml", and property "asString" is TRUE 
				// Swing does not perform any parsing : in this case we will get the result as a string

				const ruleSetDataAsString = ctx.readFile({
				    'filename': '/SysConfigGlobal/IntegrationSES/mode-citation-rules.json',
				    'format' : 'xml',
				    'asString': true
				}); 	
				var ruleSet = JSON.parse(ruleSetDataAsString);
				
				if( ruleSet.hasOwnProperty('all')){
					allRules = ruleSet['all'];
				} else {
					ctx.showNotification( "info" , "Aucune règle d'application du mode citation n'est disponible", "" , 5000 );
					return;
				}
			} catch (e) {
				ctx.showNotification( "error" , "Une erreur est survenue lors de la lecture des régles d'application du mode citation", "" , 5000 );
				console.error(e);
				return;
			}

			return allRules;
		};

		var storyBaseNode = ctx.activeDocument.getNode({xpath : "/doc/article/texte"});
		if( ! storyBaseNode ) {
			ctx.showNotification( "error" , "Aucun bloc de texte n'a été trouvé.", "" , 3000 );
			return;
		}

		// Get rules
		var quoteModeRules = readQuoteModeRulesFile(ctx);

		var wrapperTagName = "q";	// name of the tag used to enclose quotes
		var xmlContent = storyBaseNode.getXmlContent();
		var applyCount = 0;

		// Create DOM
		var dom = new DOMParser().parseFromString(xmlContent,'text/xml');

		/**
		 * apply quote mode rules to the dom
		 */
		let applyQuoteModeRules = function(rules) {
			rules.forEach(rule => {
				console.log('Apply rule : ', rule.desc, ' with xpath : ', rule.xpath);
				var xpathResult = dom.evaluate( rule.xpath, dom, null, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null );

				for ( var i=0 ; i < xpathResult.snapshotLength; i++ ){
					var thisNode = xpathResult.snapshotItem(i);
					console.log('A node is matching xpath rule : ', thisNode);
					deepCheck(thisNode);
				}

			});
		};

		let applySurroundedContents = function(node) {
			let matches = node.textContent.match(/«[^«]*?»/g);

			if(matches) {
				var m = matches[0];
				const mIndex = node.textContent.indexOf(m);
				// if the match string in enclosed in a b but we with text before and/or after, apply the quote mode
				// if the match string is enclosed in other element than b, apply the quote mode
				if( node.parentNode.nodeName !== wrapperTagName || (node.parentNode.nodeName === wrapperTagName && mIndex !== 0 && mIndex !== node.textContent.length )) {
					var range = dom.createRange();
					range.setStart(node, mIndex);
					range.setEnd(node, mIndex + m.length);

					// Création du nouveau node de citation en ajoutant le paramètre class
					var citation = dom.createElement(wrapperTagName);
					citation.classList.add('containsQuotes');
					range.surroundContents(citation);
					applyCount++;
				}

				// If multiples match in the same node (ex : <p>test with «one» and «two» guillemet</p> ) we have to process the next one
				if(matches.length > 1 && node.nextElementSibling) {
					deepCheck(node.nextElementSibling.nextSibling);
				}
			}
		};

		var deepCheck = function(node) {
			if( node.nodeType === Node.TEXT_NODE) {
				applySurroundedContents(node);
			} else if( node.nodeType === Node.ELEMENT_NODE || node.nodeType === Node.DOCUMENT_NODE) {
				for (var i = 0; i < node.childNodes.length ; i++) {
					deepCheck(node.childNodes[i]);
				}
			}
		};

		// Apply rules
		applyQuoteModeRules(quoteModeRules);

		if (applyCount > 0)
			try {
				var s = new XMLSerializer();
				var newXmlContent = s.serializeToString(dom.getRootNode());
				storyBaseNode.replaceXmlContent(newXmlContent);
				ctx.showNotification( "success" , "le mode citation a été appliqué "+applyCount+" fois.", "" , 3000 );
			} catch (e) {
				console.log('error',e);
				ctx.showNotification( "error" , "Une erreur interne est survenue lors de l'application du mode citation", "" , 4000 );
			}
		 else {
			ctx.showNotification( "success" , "Aucune citation à modifier", "" , 3000 );
		}
	}
});

eidosmedia.webclient.actions.editor.toolbar.addButton({
	action: "custom.quotemode",
    label: "Mode Citation",
    icon: 'emui-icon-add-blog',
    tabId: 'home',
    allowReadOnly: false
});