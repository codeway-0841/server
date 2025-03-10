import { default as React, FC } from 'react';
import { Link, makeStyles, Typography } from '@material-ui/core';
import { MDXProvider } from '@mdx-js/react';
import Highlight, { defaultProps, Prism } from 'prism-react-renderer';
import theme from 'prism-react-renderer/themes/github';
import clsx from 'clsx';

(typeof global !== 'undefined' ? global : window).Prism = Prism;
require('prismjs/components/prism-php');
require('prismjs/components/prism-shell-session');

const useStyles = makeStyles((t) => ({
  code: {
    borderRadius: t.shape.borderRadius,
    minWidth: 700,
    padding: '20px',
    fontFamily:
      'ui-monospace,SFMono-Regular,SF Mono,Menlo,Consolas,Liberation Mono,monospace',
  },
  inlineCode: {
    borderRadius: t.shape.borderRadius,
    backgroundColor: 'rgb(246, 248, 250)',
    color: 'rgb(57, 58, 52)',
    padding: '4px 4px',
    fontFamily:
      'ui-monospace,SFMono-Regular,SF Mono,Menlo,Consolas,Liberation Mono,monospace',
  },
  p: {
    padding: `${t.spacing(1)}px 0`,
  },
  h1: {
    fontSize: 35,
  },
  h2: {
    fontWeight: 400,
    fontSize: 26,
    marginTop: t.spacing(6),
  },
  h3: {
    fontSize: 22,
  },
}));

export const MdxProvider: FC<{
  modifyCode?: (code: string) => string;
}> = (props) => {
  const classes = useStyles();

  return (
    <MDXProvider
      components={{
        a: function A(props) {
          return <Link {...props} target="_blank" />;
        },
        p: function P(props) {
          return (
            <Typography variant="body1" className={classes.p}>
              {props.children}
            </Typography>
          );
        },
        h1: function H2(props) {
          return (
            <Typography variant="h1" className={classes.h1}>
              {props.children}
            </Typography>
          );
        },
        h2: function H2(props) {
          return (
            <Typography variant="h2" className={classes.h2}>
              {props.children}
            </Typography>
          );
        },
        h3: function H2(props) {
          return (
            <Typography variant="h3" className={classes.h3}>
              {props.children}
            </Typography>
          );
        },
        inlineCode: function InlineCode(props) {
          return (
            <span
              {...props}
              className={clsx(props.className, classes.inlineCode)}
            />
          );
        },
        code: function Code({ children, className }) {
          const language = className?.replace(/language-/, '');
          children = children?.trim();
          return (
            <Highlight
              {...defaultProps}
              theme={theme}
              code={props.modifyCode ? props.modifyCode(children) : children}
              language={language}
            >
              {({ className, style, tokens, getLineProps, getTokenProps }) => (
                <pre
                  className={clsx(className, classes.code)}
                  style={{
                    ...style,
                  }}
                >
                  {tokens.map((line, i) => (
                    <div key={i} {...getLineProps({ line, key: i })}>
                      {line.map((token, key) => (
                        <span key={key} {...getTokenProps({ token, key })} />
                      ))}
                    </div>
                  ))}
                </pre>
              )}
            </Highlight>
          );
        },
      }}
    >
      {props.children}
    </MDXProvider>
  );
};
