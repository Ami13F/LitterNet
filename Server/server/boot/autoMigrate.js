'use strict';

console.log('Start autoMigrate script');

module.exports = function (app) {
    function autoMigrateAll() {
        console.log('Start create tables from models script');
        var path = require('path');
        var app = require(path.resolve(__dirname, '../server'));
        var models = require(path.resolve(__dirname, '../model-config.json'));
        var datasources = require(path.resolve(__dirname, '../datasources.json'));
        Object.keys(models).forEach(function (key) {
            if (typeof models[key].dataSource != 'undefined') {
                if (typeof datasources[models[key].dataSource] != 'undefined') {
                    app.dataSources[models[key].dataSource].automigrate(key, function (err) {
                        if (err) throw err;
                        console.log('Model ' + key + ' migrated');
                    });
                }
            }
        });

        console.log('End create tables from models script');
    }

    // Use this when you create database
    // autoMigrateAll();
};
