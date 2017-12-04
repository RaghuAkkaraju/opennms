/*eslint-env es6 */
/* eslint no-console: 0 */

var webpack = require('webpack');
var path = require('path');
var file = require('file');
var fs = require('fs');

var AssetsPlugin = require('assets-webpack-plugin');
var BundleAnalyzerPlugin = require('webpack-bundle-analyzer').BundleAnalyzerPlugin;
var CopyWebpackPlugin = require('copy-webpack-plugin');
var ExtractTextPlugin = require('extract-text-webpack-plugin');
var HtmlWebpackPlugin = require('html-webpack-plugin');
var StringReplacePlugin = require('string-replace-webpack-plugin');
var TypedocWebpackPlugin = require('typedoc-webpack-plugin');
var WebpackMd5Hash = require('webpack-md5-hash');
var createVariants = require('parallel-webpack').createVariants;
var clonedeep = require('lodash.clonedeep');

var extractText = new ExtractTextPlugin({
  allChunks: true,
  filename: '[name]-[contenthash].css' /*,
  disable: !isProduction */
});


var pkginfo = require('./package.json');

var argv = require('yargs').argv;
var isProduction = argv.env === 'production';
var distdir = path.join(__dirname, 'target', 'dist', 'assets');
var variants = {
  production: [ false ]
};

if (isProduction) {
  variants.production = [ true, false ];
}

var styleroot = path.join(__dirname, 'src/main/assets/style');
var jsroot = path.join(__dirname, 'src/main/assets/js');

var entries = {};

/* themes/css */
file.walkSync(styleroot, function(start, dirs, names) {
  for (var file of names) {
    if (/\.s?css$/.test(file)) {
      var entry = path.basename(file, path.extname(file));
      var relative = path.relative(__dirname, path.join(start, file));
      console.log('* adding stylesheet entry point "' + entry + '": ' + relative);
      entries[entry] = path.join(styleroot, file);
    }
  }
});

/* standalone javascript utilities */
file.walkSync(path.join(jsroot, 'standalone'), function(start, dirs, names) {
  for (var file of names) {
    if (/\.m?js$/.test(file)) {
      var relative = path.relative(__dirname, path.join(start, file));
      var entry = path.basename(file, path.extname(file));
      console.log('* adding standalone javascript entry point "' + entry + '": ' + relative);
      entries[entry] = path.join(start,file);
    }
  }
});

/* javascript apps (multi-js apps with one entrypoint ("index.js") */
file.walkSync(path.join(jsroot, 'apps'), function(start, dirs, names) {
  for (var file of names) {
    if (/index\.m?js$/.test(file)) {
      var relative = path.relative(__dirname, path.join(start, file));
      // the entry name is the directory containing index.js
      var entry = path.basename(path.dirname(relative));
      console.log('* adding javascript entry point "' + entry + '": ' + relative);
      entries[entry] = path.join(start,file);
    }
  }
});

console.log('');

var config = {
  entry: entries,
  output: {
    path: distdir,
    libraryTarget: 'umd',
    umdNamedDefine: true /*,
    publicPath: 'assets/'
    */
  },
  target: 'web',
  module: {
    rules: [
      {
        test: /\.(gif|png|jpe?g|svg|eot|otf|ttf|woff2?)$/i,
        use: [{
          loader: 'file-loader',
          options: {
            name: '[name]-[hash].[ext]'
          }
        }]
      },
      {
        test: /\.scss$/,
        /* loader: 'style-loader!css-loader!group-css-media-queries-loader!sass-loader' */
        use: extractText.extract({
          fallback: 'style-loader',
          use: [
            {
              loader: StringReplacePlugin.replace({
                replacements: [
                  {
                    pattern: /\/\*! string-replace-webpack-plugin:\s*(.+?)\s*\*\//,
                    replacement: function(match, p1, offset, string) {
                      //console.log('match:',match);
                      //console.log('p1:',p1);
                      return p1;
                    }
                  }
                ]
              })
            },
            {
              loader: 'css-loader',
              options: {
                minimize: true
              }
            },
            {
              loader: 'sass-loader'
            }
          ]
        })
      },
      {
        test: /\.css$/,
        use: extractText.extract({
          fallback: 'style-loader',
          use: 'css-loader'
        })
      },
      {
        /* run tslint on typescript files before rendering */
        enforce: 'pre',
        test: /\.tsx?$/,
        use: [
          {
            loader: 'tslint-loader',
            options: {
              typeCheck: true
            }
          }
        ],
        exclude: [/node_modules/]
      },
      {
        // special case, include and load globally
        test: /\.html$/,
        use: [
          {
            loader: 'html-loader',
            options: {
              minimize: false
            }
          }
        ]
      },
      /*
      {
        // special case, include and load globally
        test: /\/(jquery|c3|d3)\.js$/,
        use: [
          {
            loader: 'script-loader'
          }
        ]
      },
      */
      {
        /* translate javascript to es2015 */
        test: /(\.m?jsx?)$/,
        use: [
          {
            loader: 'babel-loader',
            query: {
              compact: false
            }
          }
        ],
        // exclude: [/node_modules/, /(jquery|c3|d3)\.js$/]
        exclude: [/node_modules/]
      },
      {
        /* translate typescript to es2015 */
        test: /(\.tsx?)$/,
        use: [
          {
            loader: 'babel-loader',
            query: {
              compact: false
            }
          },
          {
            loader: 'ts-loader'
          }
        ],
        exclude: [/node_modules/]
      }
    ]
  },
  resolve: {
    alias: {
      /* fix a weird issue in angular-ui-bootstrap not finding its modules */
      uib: path.join(__dirname, 'node_modules', 'angular-ui-bootstrap')
    },
    modules: [
      path.resolve('./src/main/assets/modules'),
      path.resolve('./src/main/assets/js'),
      path.resolve('./node_modules')
    ],
    descriptionFiles: ['package.json', 'bower.json'],
    extensions: ['.ts', '.js']
  },
  plugins: [
    new StringReplacePlugin()
  ]
};

function getExtension(options) {
  return options.production? '.min.js' : '.js';
}

function getFile(name, options) {
  return name + getExtension(options);
}

function createConfig(options) {
  var myconf = clonedeep(config);
  //myconf.devtool = options.production? 'source-map' : 'eval-source-map';
  myconf.devtool = 'source-map';

  var defs = {
    IS_PRODUCTION: options.production,
    'global.OPENNMS_VERSION': JSON.stringify(pkginfo.version)
  };
  if (options.production) {
    defs['global.GENTLY'] = false;
  }

  var debug = Boolean(!options.production);
  var minify = Boolean(options.production);

  myconf.plugins.push(new webpack.DefinePlugin(defs));
  myconf.plugins.push(new webpack.LoaderOptionsPlugin({
    minimize: minify,
    debug: debug
  }));
  myconf.plugins.push(new webpack.ProvidePlugin({
    $: 'jquery',
    jQuery: 'jquery',
    'window.jQuery': 'jquery'
  }));
  myconf.plugins.push(new webpack.optimize.OccurrenceOrderPlugin(true));
  myconf.plugins.push(new webpack.optimize.CommonsChunkPlugin({
    name: 'requisitions-core',
    filename: getFile('[name]-[chunkhash]', options),
    minChunks: function(module) { /onms-requisitions\/lib/.test(module.resource) }
  }));
  /*
  myconf.plugins.push(new webpack.optimize.CommonsChunkPlugin({
    name: 'vendor',
    filename: getFile('vendor.[chunkhash]', options),
    minChunks: function(module) { /node_modules/.test(module.resource) }
  }));
  */
  myconf.plugins.push(new webpack.optimize.CommonsChunkPlugin('manifest'));
  myconf.plugins.push(new webpack.NamedModulesPlugin());
  myconf.plugins.push(new WebpackMd5Hash());
  myconf.plugins.push(new AssetsPlugin({
    filename: 'assets' + (options.production? '.min' : '') + '.json',
    path: distdir,
    prettyPrint: true,
    includeManifest: true
  }));
  myconf.plugins.push(extractText);

  if (options.production) {
    myconf.plugins.push(new webpack.optimize.UglifyJsPlugin({
      sourceMap: true,
      mangle: {
        except: [ '$element', '$super', '$', 'jQuery', 'exports', 'require', 'angular', 'c3', 'd3' ]
      },
      minimize: true,
      compress: true
    }));
  } else {
    //myconf.plugins.push(new BundleAnalyzerPlugin());
  }

  myconf.output.filename = getFile('[name]-[chunkhash]', options);
  myconf.output.chunkFilename = getFile('[name]-[chunkhash]', options);

  /*
  for (var entry of Object.keys(entries)) {
    var templateName = 'default.hbs';
    if (fs.existsSync(path.join(__dirname, 'src', 'templates', entry + '.hbs'))) {
      templateName = entry + '.hbs';
    }
    //console.log(entry + ': using template: ' + templateName);
    myconf.plugins.push(new HtmlWebpackPlugin({
      filename: entry + (options.production? '.min.jsp' : '.jsp'),
      inject: false,
      chunks: [entry],
      template: '!!handlebars-loader!src/templates/' + templateName
    }));
  }
  */
  myconf.plugins.push(new CopyWebpackPlugin([
    {
      from: 'src/main/assets/static'
    }
  ]));

  console.log('Building variant: production=' + Boolean(options.production));
  //console.log(myconf);

  return myconf;
}

module.exports = createVariants({}, variants, createConfig);
