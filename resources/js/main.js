(function(window, undefined){
	var Collepi = {};
	Collepi.tmpl = {};

	Collepi.updateCollection = function(){
		$.getJSON("/collection/list", function(res){
			console.log(res);
			$("#user_collection ul").html(SNBinder.bind_rowset(Collepi.tmpl.user_collection, res));
		});
	};

	$(function(){
		SNBinder.init({});

		SNBinder.get_named_sections("/static/template.htm", null, function(tmpl){
			Collepi.tmpl = tmpl;

			$.getJSON("/check/login", function(res){
				$("#login").html(SNBinder.bind(tmpl[(res.loggedin) ? "logged_in" : "not_logged_in"], res));
			});

			Collepi.updateCollection();
		});

		$("#add_isbn").bind("click", function(){
			var isbn = $("#isbn").val();
			$.post("/update/collection", {isbn: isbn}, function(res){
				if(res){
					Collepi.updateCollection();
				}
			});
		});
	});

	window.Collepi = Collepi;

})(window);
