(function(window, undefined){
	var template = {},
		snb = null,
		my_collection_page = 1,
   		my_history_page = 1;

	// util {{{
	var applyTemplate = function(obj, tmplMap) {
		if ($.isArray(obj)) {
			$.map(obj, function(v) { applyTemplate(v, tmplMap); });
		} else {
			for (key in tmplMap) {
				obj[key] = snb.bind(tmplMap[key], obj);
			}
		}
	};
	// }}}

	$.fn.bindJsLink = function(options){
		this.find(".js_link").bind("click", openJsLink);
		return this;
	};

	var addCommentFlagClass = function(comment_arr){
		$.map(comment_arr, function (v) {
			v.comment_class = (v.comment !== null) ? "has_comment" : "no_comment";
		});
	};

	var initScreen = function () {
		var hash = document.location.hash,
			val = hash.split(/\//)[1];

		if(hash.indexOf("#item") !== -1){
			openJsLinkBody("#item", val);
		} else if(hash.indexOf("#user") !== -1){
			console.log(val);
			openJsLinkBody("#user", val);
		}
	};

	// =getMyCollection
	var getMyCollection = function () {
		$.getJSON("/my/collection", { limit: 1, page: my_collection_page }, function (res) {
			applyTemplate(res, {item_small_image_link: template.ITEM_SMALL_IMAGE_LINK});
			$("#my_collection ul")
				.html(snb.bind_rowset(template.MY_COLLECTION, res))
				.bindJsLink();
		});
	};

	var nextPage = function(){
	}

	// =getMyHistory
	var getMyHistory = function () {
		$.getJSON("/my/history", { page: my_history_page }, function (res) {
			addCommentFlagClass(res);
			applyTemplate(res, { item_title_link: template.ITEM_TITLE_LINK });
			$("#my_history ul").html(snb.bind_rowset(template.MY_HISTORY, res)).bindJsLink();
		});
	};

	var openJsLink = function (e) {
		console.log("kitayo");
		var link = $(e.target),
			href = (link.attr("href") ? link : $(link.parent().get(0))).attr("href").split(/\//),
			type = href[0], val = href[1];

		openJsLinkBody(type, val);
	};

	var openJsLinkBody = function (type, val) {
		var history = null;

		if(type === "#item"){
			$("#subscreen #user").hide();
			$("#subscreen #item").show();
			$.getJSON("/item", {isbn: val}, function (res) {
				history = $.map(res.history, function (v) { return((v.comment === null) ? null : v); });
				applyTemplate(history, {item_user_link: template.ITEM_USER_LINK});

				res.item_collected_users = snb.bind_rowset(template.ITEM_COLLECTED_USERS, res.collection);
				res.item_comment = snb.bind_rowset(template.ITEM_COMMENT, history);

				$("#subscreen #item").html(snb.bind(template.ITEM, res)).bindJsLink();
			});
		} else if(type === "#user"){
			$("#subscreen #item").hide();
			$("#subscreen #user").show();
			$.getJSON("/user", {key: val}, function (res) {
				applyTemplate(res.collection, {item_small_image_link: template.ITEM_SMALL_IMAGE_LINK});
				applyTemplate(res.history, {item_title_link: template.ITEM_TITLE_LINK});
				addCommentFlagClass(res.history);

				res.my_collection = snb.bind_rowset(template.MY_COLLECTION, res.collection);
				res.my_history = snb.bind_rowset(template.MY_HISTORY, res.history);

				$("#subscreen #user").html(snb.bind(template.USER, res)).bindJsLink();
			});
		}
	};

	var getRecentCollections = function(){
		$.getJSON("/collection/list", function (res) {
			applyTemplate(res, {
				item_small_image_link: template.ITEM_SMALL_IMAGE_LINK,
				item_title_link: template.ITEM_TITLE_LINK,
				item_user_link: template.ITEM_USER_LINK
			});

			$("#recent_collections ul")
				.html(snb.bind_rowset(template.RECENT_COLLECTION, res))
				.bindJsLink();
		});
	};

	var getRecentComments = function(){
		$.getJSON("/comment/list", function (res) {
			applyTemplate(res, {item_title_link: template.ITEM_TITLE_LINK});
			$("#recent_comments ul").html(snb.bind_rowset(template.RECENT_COMMENT, res)).bindJsLink();
		});
	};

	// =updateCollection
	var updateCollection = function(isbn){
		$.ajax({
			type: "POST",
			url: "/update/collection",
			data: {
				isbn: isbn,
				read: (($("#read:checked").length === 1) ? "true" : "false"),
				comment: $("#comment").val()
			},
			dataType: "json",
			success: function(res){
				if(res){ getMyCollection(); }
			},
			complete: function(){
				getMessage();
			}
		});
	};

	// =getMessage
	var getMessage = function(){
		$.getJSON("/message", function(res){
			$("#message").html(res);
		});
	};

	$(function(){
		SNBinder.init({});
		snb = SNBinder;

		snb.get_named_sections("/static/template.htm", null, function(tmpl){
			template = tmpl;

			$.getJSON("/check/login", function(res){
				$("#login").html(snb.bind(tmpl[(res.loggedin) ? "LOGGED_IN" : "NOT_LOGGED_IN"], res));
				if(res.loggedin){
					$(".if_logged_in").show();
					getMyCollection();
					getMyHistory();
				}
			});

			getRecentCollections();
			getRecentComments();
		});

		$("#add_isbn").bind("click", function(){
			console.log("clicked");
			var isbn = $("#isbn").val();
			updateCollection(isbn);
		});

		$("#test_btn").bind("click", function(){
			console.log("read = " + (($("#read:checked").length === 1) ? "true" : "false"));
			console.log("text = " + $("#comment").val());
		});

		$("#home_link").bind("click", function(){
			$("#screen section").hide();
			$("#home").show();
		});

		getMessage();
	});

	initScreen();

	//window.Collepi = Collepi;

})(window);
