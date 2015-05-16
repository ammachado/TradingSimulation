(function() {
  'use strict';

  angular.module('myApp').controller(
      'OhlcGraphController',
      [
          '$scope',
          'alertService',
          function($scope, alertService) {
            $scope.alerts = alertService.get();
            $scope.chartSeries = [];
            var groupingUnits = [ [ 'week', [ 1 ] ], [ 'day', [ 1 ] ],
                [ 'month', [ 1, 2, 3, 6 ] ] ];

            var ws = new WebSocket('ws://localhost:9000/market/ohlc');

            ws.onmessage = function(event) {
              var symbolOhlc = JSON.parse(event.data);
              $scope.$apply(function() {
                var name = symbolOhlc.whatC.s + " to " + symbolOhlc.withC.s;
                var ohlc = symbolOhlc.ohlc;
                var ohlcSeries = $scope.chartSeries.filter(function(series) {
                  return series.name == name;
                })[0];

                if (!ohlcSeries) {
                  ohlcSeries = {
                    type : 'candlestick',
                    name : name,
                    visible : false,
                    data : []
                  };

                  if ($scope.chartSeries.length === 0) {
                    ohlcSeries.visible = true;
                  }

                  $scope.chartSeries.push(ohlcSeries);
                }

                ohlcSeries.data.push([ ohlc.timestamp, ohlc.open, ohlc.high,
                    ohlc.low, ohlc.close ]);

                if ($scope.chartConfig.loading) {
                  $scope.chartConfig.loading = false;
                }
              });
            };

            ws.onclose = function(event) {
              $scope.$apply(function() {
                alertService.add('info', 'Closed connection to the backend');
              });
            };

            ws.onerror = function(event) {
              $scope.$apply(function() {
                alertService.add('danger', 'Lost connection to the backend');
              });
            };

            $scope.chartSeries = [];

            $scope.chartConfig = {
              options : {
                // axis
                xAxis : [ {
                  type : 'datetime'
                } ],
                yAxis : [ {
                  labels : {
                    align : 'right',
                    x : -3
                  },
                  title : {
                    text : 'OHLC'
                  },
                } ],
                // navigator
                navigator : {
                  enabled : true,
                  series : {
                    data : $scope.chartSeries[0]
                  }
                },
                // plot options
                plotOptions : {
                  candlestick : {
                    color : '#5cb85c',
                    upColor : '#d9534f'
                  }
                },
                // range selector and buttons
                rangeSelector : {
                  enabled : true,
                  buttonTheme : {
                    width : null,
                    padding : 2
                  },
                  buttons : [ {
                    type : 'day',
                    count : 1,
                    text : '1 day'
                  }, {
                    type : 'day',
                    count : 7,
                    text : '1 week'
                  }, {
                    type : 'month',
                    count : 1,
                    text : '1 month'
                  }, {
                    type : 'month',
                    count : 6,
                    text : '6 months'
                  }, {
                    type : 'year',
                    count : 1,
                    text : '1 year'
                  }, {
                    type : 'all',
                    text : 'All'
                  } ]
                },
              },
              series : $scope.chartSeries,
              title : {
                text : 'Market price'
              },
              credits : {
                enabled : false
              },
              loading : true,
              size : {}
            };

          } ]);

})();