$(document).ready(function(){
    hiddenTable();

    // Ajax call to fetch weather data by country name.
    $('#countryList').change(function () {
        var country = $("#countryList option:selected").val();
        var table = $("div.blueTable");
        if(country === ''){
            table.hide();
        } else {
            table.show();
            $.ajax({
                type: "POST",
                url: "/getResultByCityName",
                dataType: "json",
                data: country,
                contentType: "application/json",
                async: false,
                cache: false,
                success: function (data) {
                    $('#city').text(data.city);
                    $('#time').text(data.datetime);
                    $('#weather').text(data.weather);
                    $('#wind').text(data.wind);
                    $('#temperature').text(data.temperature);
                }
            })
        }
    })
});

// Hide data table when country option is empty.
function hiddenTable(){
    var country = $("#countryList option:selected").val();
    var table = $("div.blueTable");
    country === ''? table.hide() : table.show();
}