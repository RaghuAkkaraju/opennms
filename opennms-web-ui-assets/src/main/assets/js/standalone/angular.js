/* Angular Core */
const angular = require('angular');
require('angular-animate');
require('angular-cookies');
require('angular-route');
require('angular-resource');
require('angular-sanitize');

/* 3rd-Party Modules */
require('angular-growl-v2');
require('angular-growl-v2/build/angular-growl.css');
require('angular-loading-bar');
require('angular-loading-bar/build/loading-bar.css');

/* Bootstrap UI */
require('standalone/bootstrap-js');
require('angular-bootstrap-checkbox');
require('angular-ui-bootstrap');

window.angular = angular;
module.exports = angular;