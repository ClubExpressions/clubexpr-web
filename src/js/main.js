window.deps = {
    'react' : require('react'),
    'react-dom' : require('react-dom'),
    'react-bootstrap' : require('react-bootstrap'),
    'auth0' : require('auth0-js'),
    'kinto' : require('kinto'),
    'react-markdown' : require('react-markdown'),
    'react-katex' : require('react-katex'),
    'react-select' : require('react-select'),
    'rc-slider' : require('rc-slider'),
    'react-checkbox-group' : require('react-checkbox-group'),
    'react-drag-sortable' : require('react-drag-sortable'),
    'react-tabs' : require('react-tabs'),
    'moment' : require('moment'),
    'react-datetime' : require('react-datetime'),
    'react-codemirror2' : require('react-codemirror2'),
    'cm-mode' : require('./codemirror/clubexpr.js'),
    'cm-matchbrackets' : require('./codemirror/matchbrackets.js'),
    'clubexpr' : require('clubexpr')
};

window.React = window.deps['react'];
window.ReactDOM = window.deps['react-dom'];
